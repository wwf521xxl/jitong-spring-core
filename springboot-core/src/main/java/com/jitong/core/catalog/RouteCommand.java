package com.jitong.core.catalog;

import java.util.List;

public record RouteCommand(String line, String name, List<String> steps) {
}
