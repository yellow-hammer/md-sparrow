/*
 * This file is a part of md-sparrow.
 *
 * Copyright (c) 2026
 * Ivan Karlo <i.karlo@outlook.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/**
 * Детерминированные UUID для golden-writer путей создания новых объектов.
 */
final class GoldenUuid {

  private GoldenUuid() {
  }

  static String from(String seed, String key) {
    Objects.requireNonNull(seed, "seed");
    Objects.requireNonNull(key, "key");
    return UUID.nameUUIDFromBytes((seed + "|" + key).getBytes(StandardCharsets.UTF_8)).toString();
  }
}
