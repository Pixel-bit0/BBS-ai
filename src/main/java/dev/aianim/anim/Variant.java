package dev.aianim.anim;

import java.util.Map;

/** One step recipe written by the AI. Angles are degrees: {rx, ry, rz}. */
public record Variant(boolean left, float stride, float bob, Map<String, float[]> contact, Map<String, float[]> passing) {}
