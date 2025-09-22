package com.iflytek.astra.console.toolkit.service.model;

import com.baomidou.mybatisplus.extension.service.IService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.iflytek.astra.console.commons.constant.ResponseEnum;
import com.iflytek.astra.console.commons.exception.BusinessException;
import com.iflytek.astra.console.toolkit.entity.table.model.ModelCategory;
import com.iflytek.astra.console.toolkit.entity.table.model.ModelCustomCategory;
import com.iflytek.astra.console.toolkit.entity.vo.CategoryTreeVO;
import com.iflytek.astra.console.toolkit.entity.vo.ModelCategoryReq;
import com.iflytek.astra.console.toolkit.mapper.model.ModelCategoryMapper;
import com.iflytek.astra.console.toolkit.mapper.model.ModelCustomCategoryMapper;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @Author clliu19
 * @Date: 2025/8/18 18:04
 */
@Service
public class ModelCategoryService extends ServiceImpl<ModelCategoryMapper, ModelCategory> implements IService<ModelCategory> {
    @Autowired
    private ModelCategoryMapper categoryMapper;
    @Autowired
    private ModelCustomCategoryMapper modelCustomCategoryMapper;

    public List<CategoryTreeVO> getTree(Long modelId) {
        List<ModelCategory> items = this.getBaseMapper().listByModelId(modelId);
        return listToTree(items);
    }

    @NotNull
    private List<CategoryTreeVO> listToTree(List<ModelCategory> list) {
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }

        // 1) First deduplicate by id
        Map<Long, ModelCategory> uniq = list.stream()
                        .collect(Collectors.toMap(
                                        ModelCategory::getId,
                                        x -> x,
                                        (a, b) -> a,
                                        LinkedHashMap::new));
        // 2) Construct all node maps
        Map<Long, CategoryTreeVO> nodeMap = new LinkedHashMap<>(uniq.size());
        Map<Long, Long> id2pid = new HashMap<>(uniq.size());
        for (ModelCategory e : uniq.values()) {
            Long id = e.getId();
            Long pid = e.getPid() == null ? 0L : e.getPid();
            id2pid.put(id, pid);

            CategoryTreeVO vo = new CategoryTreeVO(
                            id,
                            e.getKey(),
                            e.getName(),
                            Optional.ofNullable(e.getSortOrder()).orElse(0),
                            new ArrayList<>(),
                            // SYSTEM / CUSTOM
                            e.getSource());
            nodeMap.put(id, vo);
        }

        // 3) Mount each node to its parent node
        List<CategoryTreeVO> roots = new ArrayList<>();
        for (Map.Entry<Long, CategoryTreeVO> entry : nodeMap.entrySet()) {
            Long id = entry.getKey();
            Long pid = id2pid.get(id);
            CategoryTreeVO cur = entry.getValue();

            if (pid == null || pid == 0L) {
                // Root node
                roots.add(cur);
            } else {
                CategoryTreeVO parent = nodeMap.get(pid);
                if (parent != null) {
                    parent.getChildren().add(cur);
                } else {
                    // Parent node not in collection (dirty data/filtering caused), downgrade to root to avoid loss
                    roots.add(cur);
                }
            }
        }

        // 4) Unified sorting
        Comparator<CategoryTreeVO> cmp = Comparator
                        .comparingInt(CategoryTreeVO::getSortOrder)
                        .reversed()
                        .thenComparing((CategoryTreeVO x) -> x.getId(), Comparator.reverseOrder());

        // Depth-first sort for each branch
        Deque<CategoryTreeVO> stack = new ArrayDeque<>(roots);
        while (!stack.isEmpty()) {
            CategoryTreeVO node = stack.pop();
            if (node.getChildren() != null && !node.getChildren().isEmpty()) {
                node.getChildren().sort(cmp);
                // Push child nodes to stack, continue drilling down
                for (int i = node.getChildren().size() - 1; i >= 0; i--) {
                    stack.push(node.getChildren().get(i));
                }
            }
        }

        roots.sort(cmp);
        return roots;
    }

    /**
     * Save four types of model configurations (all implemented through category relationships)
     *
     * @param req Input parameter containing systemIds / customNames for each dimension
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveAll(ModelCategoryReq req) {
        if (req == null || req.getModelId() == null) {
            return;
        }
        final Long modelId = req.getModelId();
        boolean hasAnyCustom =
                        (req.getCategoryCustom() != null && StringUtils.isNotBlank(req.getCategoryCustom().getCustomName())) ||
                                        (req.getSceneCustom() != null && StringUtils.isNotBlank(req.getSceneCustom().getCustomName()));
        if (hasAnyCustom && req.getOwnerUid() == null) {
            throw new BusinessException(ResponseEnum.RESPONSE_FAILED, "Creating custom categories requires recording creator UID");
        }

        // ---------- 1) Preprocess official IDs (deduplicate, remove empty) ----------
        List<Long> catSys = safeDistinctIds(req.getCategorySystemIds());
        List<Long> sceneSys = safeDistinctIds(req.getSceneSystemIds());

        // ---------- 2) Preprocess custom items (trim names; treat blank names as not provided) ----------
        ModelCategoryReq.CustomItem catCustom = normalizeCustom(req.getCategoryCustom());
        ModelCategoryReq.CustomItem sceneCustom = normalizeCustom(req.getSceneCustom());

        // ---------- 3) Custom item parent-child dimension & status validation ----------
        // Rule: pid must exist in model_category, and p.is_delete=0, and p.key matches target dimension key
        if (catCustom != null) {
            assertParentOk(catCustom.getPid(), "modelCategory");
        }
        if (sceneCustom != null) {
            assertParentOk(sceneCustom.getPid(), "modelScenario");
        }

        // ---------- 4) Multi-select dimensions: model category / model scenario ----------
        // upsertMultiSelect(modelId, catSys, catCustom, req.getOwnerUid(), "modelCategory");
        // upsertMultiSelect(modelId, sceneSys, sceneCustom, req.getOwnerUid(), "modelScenario");
        replaceMultiSelect(modelId, "modelCategory", catSys, catCustom, req.getOwnerUid());
        replaceMultiSelect(modelId, "modelScenario", sceneSys, sceneCustom, req.getOwnerUid());

        // ---------- 5) Single-select dimensions: language support / context length ----------
        upsertSingleSelectOfficialOnly(modelId, "languageSupport", req.getLanguageSystemId());
        upsertSingleSelectOfficialOnly(modelId, "contextLengthTag", req.getContextLengthSystemId());
    }


    /**
     * Deduplicate and remove empty official ID cleanup
     *
     * @param in
     * @return
     */
    private List<Long> safeDistinctIds(List<Long> in) {
        if (in == null || in.isEmpty()) {
            return Collections.emptyList();
        }
        return in.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
    }

    /**
     * Normalize custom item: remove whitespace; return null if name is empty
     *
     * @param ci
     * @return
     */
    private ModelCategoryReq.CustomItem normalizeCustom(ModelCategoryReq.CustomItem ci) {
        if (ci == null) {
            return null;
        }
        if (ci.getCustomName() == null) {
            return null;
        }
        String name = ci.getCustomName().trim();
        if (name.isEmpty()) {
            return null;
        }
        ci.setCustomName(name);
        // pid is required, treat as invalid without pid
        return ci.getPid() == null ? null : ci;
    }

    /**
     * Ensure custom item parent node pid is valid: exists, not deleted, dimension consistent
     *
     * @param pid Official node ID to mount custom item (usually "Other")
     * @param expectedKey Expected dimension (modelCategory / modelScenario)
     */
    private void assertParentOk(Long pid, String expectedKey) {
        if (pid == null) {
            throw new BusinessException(ResponseEnum.RESPONSE_FAILED, "Custom item pid cannot be null");
        }
        // Query parent node (only need key/status columns)
        Map<String, Object> parent = categoryMapper.findCategoryKeyAndDeleteById(pid);
        if (parent == null || parent.isEmpty()) {
            throw new BusinessException(ResponseEnum.RESPONSE_FAILED, "Custom item parent node does not exist, pid=" + pid);
        }
        String parentKey = (String) parent.get("key");
        Number del = (Number) parent.get("is_delete");
        if (!expectedKey.equals(parentKey)) {
            throw new BusinessException(ResponseEnum.RESPONSE_FAILED, "Custom item parent node dimension mismatch, expected " + expectedKey + ", actual " + parentKey);
        }
        if (del != null && del.intValue() != 0) {
            throw new BusinessException(ResponseEnum.RESPONSE_FAILED, "Custom item parent node has been deleted, pid=" + pid);
        }
    }


    /**
     * Override save for a "multi-select dimension" (official multi-select + allow 0~1 custom) Behavior:
     * 1) First clean all bindings for this key under this model (official + custom) 2) Then rebuild
     * according to current input parameters
     *
     * @param modelId Model ID
     * @param key Dimension key (modelCategory / modelScenario)
     * @param systemIds Official multi-select ID collection (can be empty or empty list)
     * @param customItem Custom item (can be empty; if not empty must be normalized & validated)
     * @param ownerUid Creator UID (only used for adding custom items)
     */
    private void replaceMultiSelect(Long modelId,
                    String key,
                    List<Long> systemIds,
                    ModelCategoryReq.CustomItem customItem,
                    String ownerUid) {

        // 1) Clean up historical bindings for this dimension
        categoryMapper.deleteOfficialRelByKey(modelId, key);
        categoryMapper.deleteCustomRelByKey(modelId, key);

        // 2) Current official selection (may be empty: represents clearing)
        if (systemIds != null && !systemIds.isEmpty()) {
            List<Map<String, Long>> pairs = new ArrayList<>(systemIds.size());
            for (Long cid : systemIds) {
                if (cid == null) {
                    continue;
                }
                Map<String, Long> p = new HashMap<>(2);
                p.put("modelId", modelId);
                p.put("categoryId", cid);
                pairs.add(p);
            }
            if (!pairs.isEmpty()) {
                categoryMapper.batchInsertOfficialRel(pairs);
            }
        }

        // 3) Custom (at most 1; empty means don't create)
        if (customItem != null && StringUtils.isNotBlank(customItem.getCustomName())) {
            String name = customItem.getCustomName().trim();
            Long pid = customItem.getPid();

            // 3.1 Same-name official takes priority: avoid duplication (note: must pass key here, not pid)
            Long officialId = categoryMapper.findOfficialByKeyAndName(pid, name);
            if (officialId != null) {
                Map<String, Long> p = new HashMap<>(2);
                p.put("modelId", modelId);
                p.put("categoryId", officialId);
                categoryMapper.batchInsertOfficialRel(Collections.singletonList(p));
                return;
            }

            // 3.2 Custom duplicate check (by owner_uid + key + normalized)
            Long customId = categoryMapper.findCustomIdByKeyAndNormalized(key, ownerUid, name);
            if (customId == null) {
                // 3.3 Create new custom; pid must be "Other" for this dimension or allowed official node for
                // mounting (pre-validation ensures this)
                ModelCustomCategory mcc = new ModelCustomCategory();
                mcc.setOwnerUid(ownerUid);
                mcc.setKey(key);
                mcc.setName(name);
                mcc.setPid(pid);
                mcc.setCreateTime(new Date());
                mcc.setUpdateTime(new Date());
                modelCustomCategoryMapper.insert(mcc);
                customId = categoryMapper.findCustomIdByKeyAndNormalized(key, ownerUid, name);
            }

            if (customId != null) {
                Map<String, Long> p = new HashMap<>(2);
                p.put("modelId", modelId);
                p.put("customId", customId);
                categoryMapper.batchInsertCustomRel(Collections.singletonList(p));
            }
        }
    }

    /**
     * Multi-select dimension save (official+custom can coexist) - Official: batch write relationships
     * (idempotent) - Custom: first absorb official duplicates; otherwise check duplicates then add and
     * write relationships
     */
    @Transactional(rollbackFor = Exception.class)
    public void upsertMultiSelect(Long modelId,
                    List<Long> systemIds,
                    ModelCategoryReq.CustomItem customNames,
                    String ownerUid,
                    String key) {

        // Official items batch binding (idempotent)
        if (systemIds != null && !systemIds.isEmpty()) {
            List<Map<String, Long>> pairs = new ArrayList<>(systemIds.size());
            for (Long cid : systemIds) {
                Map<String, Long> p = new HashMap<>(2);
                p.put("modelId", modelId);
                p.put("categoryId", cid);
                pairs.add(p);
            }
            categoryMapper.batchInsertOfficialRel(pairs);
        }

        // Custom item processing
        if (customNames != null && StringUtils.isNotBlank(customNames.getCustomName())) {
            String name = customNames.getCustomName().trim();
            Long pid = customNames.getPid();
            List<Map<String, Long>> customPairs = new ArrayList<>();
            // 1) If same name as official, process as official directly to avoid duplication
            Long officialId = categoryMapper.findOfficialByKeyAndName(pid, name);
            if (officialId != null) {
                Map<String, Long> p = new HashMap<>(2);
                p.put("modelId", modelId);
                p.put("categoryId", officialId);
                categoryMapper.batchInsertOfficialRel(Collections.singletonList(p));
            } else {
                // 2) Check if there's already a custom with the same normalized name
                Long customId = categoryMapper.findCustomIdByKeyAndNormalized(key, ownerUid, name);
                if (customId == null) {
                    // 3) Create new custom; pid can be null, will fallback to official top level of this key when
                    // querying tree
                    ModelCustomCategory modelCustomCategory = new ModelCustomCategory();
                    modelCustomCategory.setKey(key);
                    modelCustomCategory.setOwnerUid(ownerUid);
                    modelCustomCategory.setPid(pid);
                    modelCustomCategory.setName(name);
                    modelCustomCategory.setCreateTime(new Date());
                    modelCustomCategory.setUpdateTime(new Date());
                    modelCustomCategoryMapper.insert(modelCustomCategory);
                    customId = categoryMapper.findCustomIdByKeyAndNormalized(key, ownerUid, name);
                }

                Map<String, Long> p = new HashMap<>(2);
                p.put("modelId", modelId);
                p.put("customId", customId);
                customPairs.add(p);
            }

            if (!customPairs.isEmpty()) {
                categoryMapper.batchInsertCustomRel(customPairs);
            }
        }
    }

    /**
     * Single-select dimension save (official only) - Ensure only one binding per key through "delete
     * then insert" - Defensive cleanup of custom bindings (even if frontend doesn't allow custom)
     */
    @Transactional(rollbackFor = Exception.class)
    public void upsertSingleSelectOfficialOnly(Long modelId, String key, Long newOfficialId) {
        // Clean up all old bindings for this key (official + custom)
        categoryMapper.deleteOfficialRelByKey(modelId, key);
        categoryMapper.deleteCustomRelByKey(modelId, key);

        if (newOfficialId != null) {
            Map<String, Long> pair = new HashMap<>(2);
            pair.put("modelId", modelId);
            pair.put("categoryId", newOfficialId);
            categoryMapper.batchInsertOfficialRel(Collections.singletonList(pair));
        }
    }

    /**
     * Used when creating models: return complete official category tree (excluding custom) No query
     * parameters; only filter is_delete = 0
     */
    public List<CategoryTreeVO> getAllCategoryTree() {
        List<ModelCategory> rows = categoryMapper.listAllTree();
        return toTree(rows);
    }

    /**
     * Flat -> Tree (supports arbitrary levels) Rules: - Root: pid == 0 (or null treated as 0) -
     * Sorting: parent/child both by sortOrder DESC, id DESC - Deduplication: deduplicate by id, avoid
     * SQL/data adjustment generated duplicates - Fault tolerance: promote child nodes to root when
     * parent nodes are missing, avoid data loss
     */
    @NotNull
    private List<CategoryTreeVO> toTree(List<ModelCategory> list) {
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }

        // 1) Deduplicate by id while maintaining order
        Map<Long, ModelCategory> uniq = list.stream()
                        .collect(
                                        Collectors.toMap(ModelCategory::getId, x -> x, (a, b) -> a, LinkedHashMap::new));

        // 2) Create all nodes first (without mounting)
        Map<Long, CategoryTreeVO> nodeMap = new LinkedHashMap<>(uniq.size());
        Map<Long, Long> id2pid = new HashMap<>(uniq.size());
        for (ModelCategory e : uniq.values()) {
            Long id = e.getId();
            Long pid = e.getPid() == null ? 0L : e.getPid();
            id2pid.put(id, pid);
            nodeMap.put(id, new CategoryTreeVO(
                            id,
                            e.getKey(),
                            e.getName(),
                            Optional.ofNullable(e.getSortOrder()).orElse(0),
                            new ArrayList<>(),
                            "SYSTEM"));
        }

        // 3) Mount under parent nodes
        List<CategoryTreeVO> roots = new ArrayList<>();
        for (Map.Entry<Long, CategoryTreeVO> entry : nodeMap.entrySet()) {
            Long id = entry.getKey();
            Long pid = id2pid.get(id);
            CategoryTreeVO cur = entry.getValue();

            if (pid == null || pid == 0L) {
                roots.add(cur);
            } else {
                CategoryTreeVO parent = nodeMap.get(pid);
                if (parent != null) {
                    parent.getChildren().add(cur);
                } else {
                    // Parent missing fault tolerance: return as root to avoid data loss
                    roots.add(cur);
                }
            }
        }

        // 4) Unified sorting (parent/child)
        Comparator<CategoryTreeVO> cmp = Comparator
                        .comparingInt(CategoryTreeVO::getSortOrder)
                        .reversed()
                        .thenComparing((CategoryTreeVO x) -> x.getId(), Comparator.reverseOrder());

        Deque<CategoryTreeVO> stack = new ArrayDeque<>(roots);
        while (!stack.isEmpty()) {
            CategoryTreeVO n = stack.pop();
            if (n.getChildren() != null && !n.getChildren().isEmpty()) {
                n.getChildren().sort(cmp);
                // Depth-first sort all levels
                for (int i = n.getChildren().size() - 1; i >= 0; i--) {
                    stack.push(n.getChildren().get(i));
                }
            }
        }
        roots.sort(cmp);
        return roots;
    }
}
