package com.voidguard.anticheat.core;

/** Bir kontrolün ürettiği kanıt. weight: şüphe puanı katkısı, detail: log için okunabilir açıklama. */
public final class Result {
    private final double weight;
    private final String detail;
    public Result(double weight, String detail) {
        this.weight = weight;
        this.detail = detail;
    }
    public double weight() { return weight; }
    public String detail() { return detail; }
    public static Result of(double weight, String detail) { return new Result(weight, detail); }
}
