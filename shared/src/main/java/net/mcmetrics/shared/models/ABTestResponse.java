package net.mcmetrics.shared.models;

import java.util.List;

public class ABTestResponse {
    public boolean success;
    public Data data;

    public static class Data {
        public List<ABTest> ab_tests;
    }
}