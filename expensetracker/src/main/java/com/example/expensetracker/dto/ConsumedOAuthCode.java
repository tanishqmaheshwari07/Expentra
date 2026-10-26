package com.example.expensetracker.dto;

public record ConsumedOAuthCode(Long userId, String binderHash) {}
