package com.iflytek.astron.console.toolkit.service.tool;

import com.alibaba.fastjson2.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.astron.console.commons.constant.ResponseEnum;
import com.iflytek.astron.console.commons.exception.BusinessException;
import com.iflytek.astron.console.commons.util.space.SpaceInfoUtil;
import com.iflytek.astron.console.toolkit.entity.table.tool.*;
import com.iflytek.astron.console.toolkit.entity.tool.*;
import com.iflytek.astron.console.toolkit.handler.RpaHandler;
import com.iflytek.astron.console.toolkit.handler.UserInfoManagerHandler;
import com.iflytek.astron.console.toolkit.mapper.tool.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service layer for managing user RPA assistants.
 * <p>
 * Provides APIs for creating, querying, updating, deleting RPA assistants, validating fields, and
 * integrating with platform APIs.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RpaAssistantService {

    private final RpaUserAssistantMapper assistantMapper;
    private final RpaUserAssistantFieldMapper fieldMapper;
    private final RpaInfoMapper rpaInfoMapper;
    private final RpaHandler rpaHandler;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Create an RPA assistant with plaintext credentials.
     *
     * @param currentUserId current user ID
     * @param req creation request
     * @return created assistant response
     * @throws IllegalArgumentException if the platform does not exist or field validation fails
     */
    @Transactional
    public RpaAssistantResp create(String currentUserId, CreateRpaAssistantReq req) {
        // 0. Idempotency check: same user, same assistant name is not allowed
        long exists = assistantMapper.selectCount(
                new LambdaQueryWrapper<RpaUserAssistant>()
                        .eq(RpaUserAssistant::getUserId, currentUserId)
                        .eq(RpaUserAssistant::getAssistantName, req.assistantName()));
        if (exists > 0) {
            throw new BusinessException(ResponseEnum.RESPONSE_FAILED, "Assistant name already exists: " + req.assistantName());
        }

        // 1. Read rpa_info platform definition and parse field specs
        List<PlatformFieldSpec> specs = loadPlatformSpecs(req.platformId());
        Map<String, PlatformFieldSpec> specMap = specs.stream()
                .collect(Collectors.toMap(PlatformFieldSpec::getName, s -> s, (a, b) -> a));

        // 2. Validate required fields and types (only required & string check for now)
        validate(specMap, req.fields());

        // 3. Insert main assistant record
        RpaUserAssistant assistant = new RpaUserAssistant();
        assistant.setUserId(currentUserId);
        assistant.setPlatformId(req.platformId());
        assistant.setAssistantName(req.assistantName());
        assistant.setStatus(1);
        assistant.setSpaceId(SpaceInfoUtil.getSpaceId());
        assistant.setIcon(req.icon());
        assistant.setRemarks(req.remarks());
        assistant.setCreateTime(LocalDateTime.now());
        assistant.setUpdateTime(LocalDateTime.now());
        assistantMapper.insert(assistant);

        // 4. Insert field values (plaintext)
        for (Map.Entry<String, String> e : req.fields().entrySet()) {
            PlatformFieldSpec s = specMap.get(e.getKey());
            if (s == null) {
                // Not defined in spec: ignore; or throw error if required
                continue;
            }
            RpaUserAssistantField f = new RpaUserAssistantField();
            f.setAssistantId(assistant.getId());
            f.setFieldKey(s.getName());
            f.setFieldName(s.getKey());
            f.setFieldValue(e.getValue());
            f.setCreateTime(LocalDateTime.now());
            f.setUpdateTime(LocalDateTime.now());
            fieldMapper.insert(f);
        }
        // 5. Assemble response (return as-is based on request)
        return new RpaAssistantResp(
                assistant.getId(),
                assistant.getPlatformId(),
                assistant.getAssistantName(),
                assistant.getRemarks(),
                assistant.getStatus(),
                req.fields(),
                new JSONArray(),
                assistant.getCreateTime());
    }

    /**
     * Load platform field specifications.
     *
     * @param platformId platform ID
     * @return list of field specifications
     * @throws BusinessException if the platform does not exist or JSON parsing fails
     */
    private List<PlatformFieldSpec> loadPlatformSpecs(Long platformId) {
        RpaInfo rpaInfo = rpaInfoMapper.selectById(platformId);
        String json = rpaInfo.getValue();
        if (json == null || json.isBlank()) {
            throw new BusinessException(ResponseEnum.RESPONSE_FAILED, "Platform does not exist or has no field definitions, platformId=" + platformId);
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<PlatformFieldSpec>>() {});
        } catch (Exception e) {
            log.error("Failed to parse platform field definition:", e);
            throw new BusinessException(ResponseEnum.RESPONSE_FAILED, "Failed to parse platform field definition");
        }
    }

    /**
     * Validate required fields and field types.
     *
     * @param specMap platform field specification map
     * @param fields actual field key-value pairs
     * @throws BusinessException if required fields are missing or validation fails
     */
    private void validate(Map<String, PlatformFieldSpec> specMap, Map<String, String> fields) {
        // Required fields check
        List<String> missing = specMap.values()
                .stream()
                .filter(PlatformFieldSpec::isRequired)
                .map(PlatformFieldSpec::getName)
                .filter(n -> fields == null || !fields.containsKey(n) ||
                        fields.get(n) == null || fields.get(n).isBlank())
                .toList();
        if (!missing.isEmpty()) {
            throw new BusinessException(ResponseEnum.RESPONSE_FAILED, "Missing required fields: " + String.join(",", missing));
        }
        // Type validation (simple demo: only string type is allowed; can extend to number/bool/url/regex
        // etc.)
        if (fields != null) {
            for (Map.Entry<String, String> e : fields.entrySet()) {
                PlatformFieldSpec s = specMap.get(e.getKey());
                if (s == null)
                    continue;
                String t = Optional.ofNullable(s.getType()).orElse("string").toLowerCase();
                if (!"string".equals(t)) {
                    // Extend validation for other types here
                }
            }
        }
    }

    /**
     * Get assistant details including robots fetched from RPA platform.
     *
     * @param currentUserId current user ID
     * @param assistantId assistant ID
     * @param name optional robot name filter (supports Chinese "name" or English "english_name")
     * @return assistant detail response with robots and fields
     * @throws BusinessException if assistant does not exist, has no permission, or RPA platform fields
     *         are missing
     */
    @Transactional(rollbackFor = Exception.class)
    public RpaAssistantResp detail(String currentUserId, Long assistantId, String name) {
        // 1) Basic info and ownership check
        RpaUserAssistant a = findByIdAndUser(assistantId, currentUserId);

        // 2) Retrieve authentication field (e.g., apiKey)
        RpaUserAssistantField field = fieldMapper.selectOne(
                new LambdaQueryWrapper<RpaUserAssistantField>()
                        .eq(RpaUserAssistantField::getAssistantId, a.getId()));
        if (field == null || StringUtils.isBlank(field.getFieldValue())) {
            throw new BusinessException(ResponseEnum.RESPONSE_FAILED, "RPA platform authentication field is missing, please configure first");
        }

        // 3) Fetch platform robot list (fixed 1/100, can be extended to dynamic pagination)
        JSONObject rpaList = rpaHandler.getRpaList(1, 100, field.getFieldValue());

        // 4) Update robot count with actual platform total (not affected by name filter)
        Integer total = rpaList.getInteger("total");
        if (total == null)
            total = 0;
        a.setRobotCount(total);
        assistantMapper.updateById(a);

        // 5) Filter records by name if provided
        JSONArray records = rpaList.getJSONArray("records");
        if (records == null) {
            records = new JSONArray();
        }

        if (StringUtils.isNotBlank(name)) {
            final String q = name.trim();
            JSONArray filtered = new JSONArray(records.size());
            for (Object record : records) {
                if (!(record instanceof JSONObject obj)) {
                    continue;
                }
                String nameCn = obj.getString("name");
                String nameEn = obj.getString("english_name");
                boolean hitCn = StringUtils.contains(nameCn, q);
                boolean hitEn = StringUtils.containsIgnoreCase(nameEn == null ? "" : nameEn, q);
                if (hitCn || hitEn) {
                    filtered.add(obj);
                }
            }
            records = filtered;
        }

        // 6) Return detail response
        Map<String, String> fields = loadFieldsAsMap(assistantId);
        return new RpaAssistantResp(
                a.getId(),
                a.getPlatformId(),
                a.getAssistantName(),
                a.getRemarks(),
                a.getStatus(),
                fields,
                records,
                a.getCreateTime());
    }

    /**
     * Update assistant info.
     *
     * @param currentUserId current user ID
     * @param assistantId assistant ID
     * @param req update request
     * @return updated assistant entity
     * @throws BusinessException if assistant does not exist, no permission, or name duplication occurs
     */
    @Transactional
    public RpaUserAssistant update(String currentUserId, Long assistantId, UpdateRpaAssistantReq req) {
        RpaUserAssistant a = findByIdAndUser(assistantId, currentUserId);

        // Duplicate name check
        if (req.assistantName() != null && !req.assistantName().isBlank()
                && !req.assistantName().equals(a.getAssistantName())) {
            long dup = assistantMapper.selectCount(Wrappers.<RpaUserAssistant>lambdaQuery()
                    .eq(RpaUserAssistant::getUserId, currentUserId)
                    .eq(RpaUserAssistant::getAssistantName, req.assistantName()));
            if (dup > 0) {
                throw new BusinessException(ResponseEnum.RESPONSE_FAILED, "RPA assistant name already exists: " + req.assistantName());
            }
            a.setAssistantName(req.assistantName());
        }
        if (req.status() != null)
            a.setStatus(req.status());
        a.setUpdateTime(LocalDateTime.now());
        assistantMapper.updateById(a);

        // Field update logic
        boolean replace = Boolean.TRUE.equals(req.replaceFields());
        Map<String, String> origin = loadFieldsAsMap(assistantId);
        Map<String, String> finalFields;

        if (replace) {
            // Replace all fields
            fieldMapper.delete(Wrappers.<RpaUserAssistantField>lambdaQuery()
                    .eq(RpaUserAssistantField::getAssistantId, assistantId));
            finalFields = Optional.ofNullable(req.fields()).orElseGet(HashMap::new);
            if (!finalFields.isEmpty()) {
                Map<String, PlatformFieldSpec> specMap = toSpecMap(loadPlatformSpecs(a.getPlatformId()));
                validate(specMap, finalFields);
                saveFields(assistantId, specMap, finalFields);
            }
        } else {
            // Merge fields: delete, then overwrite/add
            if (req.fields() != null && !req.fields().isEmpty()) {
                Map<String, PlatformFieldSpec> specMap = toSpecMap(loadPlatformSpecs(a.getPlatformId()));
                finalFields = new HashMap<>(origin);
                finalFields.putAll(req.fields());
                validate(specMap, finalFields);

                for (Map.Entry<String, String> e : req.fields().entrySet()) {
                    RpaUserAssistantField exist = fieldMapper.selectOne(Wrappers.<RpaUserAssistantField>lambdaQuery()
                            .eq(RpaUserAssistantField::getAssistantId, assistantId)
                            .eq(RpaUserAssistantField::getFieldKey, e.getKey()));
                    if (exist == null) {
                        RpaUserAssistantField f = new RpaUserAssistantField();
                        var spec = specMap.get(e.getKey());
                        f.setAssistantId(assistantId);
                        f.setFieldKey(e.getKey());
                        f.setFieldName(spec != null ? spec.getKey() : e.getKey());
                        f.setFieldValue(e.getValue());
                        f.setCreateTime(LocalDateTime.now());
                        f.setUpdateTime(LocalDateTime.now());
                        fieldMapper.insert(f);
                    } else {
                        exist.setFieldValue(e.getValue());
                        exist.setUpdateTime(LocalDateTime.now());
                        fieldMapper.updateById(exist);
                    }
                }
            } else {
                finalFields = loadFieldsAsMap(assistantId);
            }
        }
        return a;
    }

    /**
     * Delete an assistant.
     *
     * @param currentUserId current user ID
     * @param assistantId assistant ID
     * @throws BusinessException if assistant does not exist or no permission
     */
    @Transactional
    public void delete(String currentUserId, Long assistantId) {
        findByIdAndUser(assistantId, currentUserId);
        assistantMapper.deleteById(assistantId);
    }

    /* —— Helper Methods —— */

    private RpaUserAssistant findByIdAndUser(Long id, String userId) {
        RpaUserAssistant a = assistantMapper.selectOne(Wrappers.<RpaUserAssistant>lambdaQuery()
                .eq(RpaUserAssistant::getId, id)
                .eq(RpaUserAssistant::getUserId, userId));
        if (a == null) {
            throw new BusinessException(ResponseEnum.RESPONSE_FAILED, "Assistant does not exist or no permission");
        }
        return a;
    }

    private Map<String, String> loadFieldsAsMap(Long assistantId) {
        List<RpaUserAssistantField> list = fieldMapper.selectList(
                Wrappers.<RpaUserAssistantField>lambdaQuery()
                        .eq(RpaUserAssistantField::getAssistantId, assistantId));
        return list.stream()
                .collect(Collectors.toMap(
                        RpaUserAssistantField::getFieldKey,
                        RpaUserAssistantField::getFieldValue,
                        (a, b) -> a,
                        LinkedHashMap::new));
    }

    private void saveFields(Long assistantId, Map<String, PlatformFieldSpec> specMap, Map<String, String> fields) {
        if (fields == null || fields.isEmpty())
            return;
        for (Map.Entry<String, String> e : fields.entrySet()) {
            PlatformFieldSpec s = specMap.get(e.getKey());
            if (s == null)
                continue;
            RpaUserAssistantField f = new RpaUserAssistantField();
            f.setAssistantId(assistantId);
            f.setFieldKey(s.getName());
            f.setFieldName(s.getKey());
            f.setFieldValue(e.getValue());
            f.setCreateTime(LocalDateTime.now());
            f.setUpdateTime(LocalDateTime.now());
            fieldMapper.insert(f);
        }
    }

    private Map<String, PlatformFieldSpec> toSpecMap(List<PlatformFieldSpec> specs) {
        return specs.stream().collect(Collectors.toMap(PlatformFieldSpec::getName, s -> s, (a, b) -> a));
    }

    /**
     * Get the list of assistants for the current user, optionally filtered by name.
     *
     * @param name assistant name filter (optional, fuzzy match)
     * @return list of assistants
     */
    public List<RpaUserAssistant> getList(String name) {
        String userId = UserInfoManagerHandler.getUserId();
        return assistantMapper.selectList(new LambdaQueryWrapper<RpaUserAssistant>()
                .eq(RpaUserAssistant::getUserId, userId)
                .like(StringUtils.isNoneBlank(name), RpaUserAssistant::getAssistantName, name)
                .orderByDesc(RpaUserAssistant::getUpdateTime));
    }
}
