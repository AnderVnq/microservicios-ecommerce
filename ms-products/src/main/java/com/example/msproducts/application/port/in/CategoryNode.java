package com.example.msproducts.application.port.in;

import java.util.List;

public record CategoryNode(Long id, String name, int level, boolean active, List<CategoryNode> children) {
}
