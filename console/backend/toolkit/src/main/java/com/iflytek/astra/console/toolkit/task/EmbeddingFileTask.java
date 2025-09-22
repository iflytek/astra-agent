package com.iflytek.astra.console.toolkit.task;


import com.iflytek.astra.console.toolkit.service.repo.FileInfoV2Service;

public class EmbeddingFileTask implements Runnable {
    private final FileInfoV2Service fileInfoV2Service;
    private final Long fileId;
    private final Long spaceId;

    public EmbeddingFileTask(FileInfoV2Service fileInfoV2Service, Long fileId, Long spaceId) {
        this.fileInfoV2Service = fileInfoV2Service;
        this.fileId = fileId;
        this.spaceId = spaceId;
    }

    @Override
    public void run() {
        fileInfoV2Service.embeddingFile(fileId, spaceId);
    }
}
