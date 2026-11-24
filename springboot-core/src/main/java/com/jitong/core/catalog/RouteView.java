package com.jitong.core.catalog;

import java.util.List;

public record RouteView(String key, String line, String name, List<RouteStepView> steps) {
}
