/*
 * This file is a part of md-sparrow.
 *
 * Copyright (c) 2026
 * Ivan Karlo <i.karlo@outlook.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package io.github.yellowhammer.designerxml.cf;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.yellowhammer.designerxml.Ssl31SubmodulePaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

class RoleRightsFileTest {

  @TempDir Path tempDir;

  private Path copyRole() throws Exception {
    Path roles = Ssl31SubmodulePaths.projectRoot().resolve("src/cf/Roles");
    Path source = Files.list(roles).filter(p -> p.toString().endsWith(".xml")).findFirst().orElseThrow();
    String stem = source.getFileName().toString().replace(".xml", "");
    Path roleXml = tempDir.resolve(source.getFileName());
    Files.copy(source, roleXml);
    Path rights = roles.resolve(stem).resolve("Ext").resolve("Rights.xml");
    Path target = tempDir.resolve(stem).resolve("Ext").resolve("Rights.xml");
    Files.createDirectories(target.getParent());
    Files.copy(rights, target);
    return roleXml;
  }

  @Test
  void readsFlagsAndObjects() throws Exception {
    Path roleXml = copyRole();
    RoleRightsFile.Dto dto = RoleRightsFile.read(roleXml);
    assertThat(dto.objects).isNotEmpty();
    assertThat(dto.objects.get(0).rights).isNotEmpty();
  }

  @Test
  void grantAndRevokeRoundTrip() throws Exception {
    Path roleXml = copyRole();
    RoleRightsFile.Dto before = RoleRightsFile.read(roleXml);
    String existingObject = before.objects.get(0).name;
    String existingRight = before.objects.get(0).rights.get(0).name;

    RoleRightsFile.Edit grant = new RoleRightsFile.Edit();
    grant.object = "Catalog._ДемоНоменклатура";
    grant.right = "Read";
    grant.value = true;
    RoleRightsFile.Edit revoke = new RoleRightsFile.Edit();
    revoke.object = existingObject;
    revoke.right = existingRight;
    revoke.value = false;
    RoleRightsFile.applyEdits(roleXml, List.of(grant, revoke));

    RoleRightsFile.Dto after = RoleRightsFile.read(roleXml);
    assertThat(after.objects)
      .anyMatch(item -> "Catalog._ДемоНоменклатура".equals(item.name)
        && item.rights.stream().anyMatch(right -> "Read".equals(right.name) && right.value));
    assertThat(after.objects.stream().filter(item -> existingObject.equals(item.name)).findFirst())
      .satisfies(found -> {
        if (found.isPresent()) {
          assertThat(found.get().rights).noneMatch(right -> existingRight.equals(right.name));
        }
      });
  }
}
