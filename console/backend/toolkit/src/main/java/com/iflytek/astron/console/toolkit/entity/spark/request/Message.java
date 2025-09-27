package com.iflytek.astron.console.toolkit.entity.spark.request;

import com.iflytek.astron.console.toolkit.entity.spark.Text;
import lombok.Data;

import java.util.List;

@Data
public class Message {
    List<Text> text;
}
