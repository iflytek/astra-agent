package com.iflytek.astron.console.toolkit.entity.spark.response;

import com.iflytek.astron.console.toolkit.entity.spark.Text;
import lombok.Data;

import java.util.List;

@Data
public class Choices {
    Integer status;
    Integer seq;
    List<Text> text;
}
