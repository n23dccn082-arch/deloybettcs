package com.taskmanager.dto.response;

public record BurndownPoint(
    String date,
    double ideal,
    Double actual,
    double overdue,
    boolean sprintEnd
) {}
