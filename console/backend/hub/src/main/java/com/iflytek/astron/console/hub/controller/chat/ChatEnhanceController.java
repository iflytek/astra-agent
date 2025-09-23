package com.iflytek.astron.console.hub.controller.chat;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.iflytek.astron.console.commons.constant.ResponseEnum;
import com.iflytek.astron.console.commons.entity.bot.BotChatFileParam;
import com.iflytek.astron.console.commons.entity.chat.ChatFileUser;
import com.iflytek.astron.console.commons.exception.BusinessException;
import com.iflytek.astron.console.commons.response.ApiResult;
import com.iflytek.astron.console.commons.service.data.ChatDataService;
import com.iflytek.astron.console.commons.util.RequestContextUtil;
import com.iflytek.astron.console.commons.service.data.ChatListDataService;
import com.iflytek.astron.console.hub.dto.chat.ChatEnhanceSaveFileVo;
import com.iflytek.astron.console.commons.entity.chat.ChatList;
import com.iflytek.astron.console.commons.entity.chat.ChatTreeIndex;
import com.iflytek.astron.console.hub.dto.chat.LongFileDto;
import com.iflytek.astron.console.hub.service.chat.ChatEnhanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * @author yingpeng
 */
@RestController
@Slf4j
@Tag(name = "Chat Enhancement")
@RequestMapping("/chat-enhance")
public class ChatEnhanceController {

    @Autowired
    private ChatListDataService chatListDataService;

    @Autowired
    private ChatEnhanceService chatEnhanceService;
    @Autowired
    private ChatDataService chatDataService;

    @PostMapping(path = "/save-file")
    @Operation(summary = "Save File")
    public ApiResult<String> saveFile(@RequestBody ChatEnhanceSaveFileVo vo) {
        String uid = RequestContextUtil.getUID();
        // Get the latest chat_id
        if (ObjectUtil.isNotEmpty(vo.getChatId()) && vo.getChatId() != 0) {
            Long chatId = vo.getChatId();
            // Get the latest chat_id
            List<ChatTreeIndex> chatTreeIndexList = chatListDataService.findChatTreeIndexByChatIdOrderById(chatId);
            if (chatTreeIndexList.isEmpty()) {
                return ApiResult.error(ResponseEnum.DATA_NOT_FOUND);
            }
            chatId = chatTreeIndexList.getFirst().getChildChatId();
            ChatList chatList = chatListDataService.findByUidAndChatId(uid, chatId);
            if (chatList == null || chatList.getEnable() == 0) {
                log.error("User: {} uploaded file with incorrect chatId information: {}", uid, vo);
                throw new BusinessException(ResponseEnum.LONG_CONTENT_CHAT_ID_ERROR);
            }
            // Set the latest chatId
            vo.setChatId(chatId);
        }
        Map<String, String> saveFileReq = chatEnhanceService.saveFile(uid, vo);
        String fileId = saveFileReq.get("file_id");
        String errorMsg = saveFileReq.get("error_msg");
        if (StringUtils.isNotBlank(fileId)) {
            return ApiResult.success(fileId);
        }
        // If fileId is empty, return error message
        return ApiResult.error(-1, errorMsg);
    }

    @Operation(summary = "Unbind file's FileId and ChatId")
    @PostMapping(path = "unbind-file")
    public ApiResult<Object> unbindFile(@RequestBody LongFileDto longFileDto) {
        if (StringUtils.isBlank(longFileDto.getChatId())) {
            throw new BusinessException(ResponseEnum.LONG_CONTENT_MISS_FILE_INFO);
        }
        Long chatId = Long.valueOf(longFileDto.getChatId());
        String fileId = longFileDto.getFileId();
        String linkIdString = longFileDto.getLinkId();

        if (StringUtils.isBlank(fileId) && StringUtils.isBlank(linkIdString)) {
            throw new BusinessException(ResponseEnum.LONG_CONTENT_MISS_FILE_INFO);
        }
        String uid = RequestContextUtil.getUID();
        // Get the latest chat_id
        List<ChatTreeIndex> chatTreeIndexList = chatListDataService.findChatTreeIndexByChatIdOrderById(chatId);
        if (chatTreeIndexList.isEmpty()) {
            throw new BusinessException(ResponseEnum.DATA_NOT_FOUND);
        }
        chatId = chatTreeIndexList.getFirst().getChildChatId();
        ChatList chatList = chatListDataService.findByUidAndChatId(uid, chatId);
        if (chatList == null || chatList.getEnable() == 0) {
            throw new BusinessException(ResponseEnum.LONG_CONTENT_CHAT_ID_ERROR);
        }
        // Logical deletion of chatFileReq (unbind file from chatID)
        if (StringUtils.isNotBlank(linkIdString)) {
            ChatFileUser chatFileUser = chatEnhanceService.findById(Long.valueOf(linkIdString), uid);
            if (chatFileUser == null || chatFileUser.getFileId() == null) {
                throw new BusinessException(ResponseEnum.FILE_NOT_PROCESS);
            }
            fileId = chatFileUser.getFileId();
        }
        chatEnhanceService.delete(fileId, chatId, uid);

        if (StrUtil.isNotEmpty(longFileDto.getParamName())) {
            List<BotChatFileParam> oneByChatIdAndNameList = chatDataService.findAllBotChatFileParamByChatIdAndNameAndIsDelete(chatId, longFileDto.getParamName(), 0);
            for (BotChatFileParam oneByChatIdAndNameAndIsDelete : oneByChatIdAndNameList) {
                int i = oneByChatIdAndNameAndIsDelete.getFileIds().indexOf(fileId);
                if (i >= 0) {
                    oneByChatIdAndNameAndIsDelete.getFileIds().remove(i);
                    oneByChatIdAndNameAndIsDelete.getFileUrls().remove(i);
                    oneByChatIdAndNameAndIsDelete.setUpdateTime(LocalDateTime.now());
                    chatDataService.updateBotChatFileParam(oneByChatIdAndNameAndIsDelete);
                }
            }
        }
        return ApiResult.success();
    }
}
