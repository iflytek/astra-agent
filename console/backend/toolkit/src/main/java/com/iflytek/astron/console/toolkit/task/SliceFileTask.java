package com.iflytek.astron.console.toolkit.task;

import com.iflytek.astron.console.toolkit.entity.pojo.SliceConfig;
import com.iflytek.astron.console.toolkit.service.repo.FileInfoV2Service;

import java.util.concurrent.Callable;

public class SliceFileTask implements Callable<Boolean> {
    private final FileInfoV2Service fileInfoV2Service;
    private final Long fileId;
    private final SliceConfig sliceConfig;
    private final Integer backEmbedding;

    public SliceFileTask(FileInfoV2Service fileInfoV2Service, Long fileId, SliceConfig sliceConfig, Integer backEmbedding) {
        this.fileInfoV2Service = fileInfoV2Service;
        this.fileId = fileId;
        this.sliceConfig = sliceConfig;
        this.backEmbedding = backEmbedding;
    }


    @Override
    public Boolean call() {
        return fileInfoV2Service.sliceFile(fileId, sliceConfig, backEmbedding).isParseSuccess();
    }
}
