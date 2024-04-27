package com.shepherdmoney.interviewproject.vo.request;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

@Data
public class UpdateBalancePayload {

    private String creditCardNumber;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy/MM/dd")
    private LocalDate balanceDate;

    private double balanceAmount;
}
