package io.github.yellowhammer.designerxml.cf;

import io.github.yellowhammer.designerxml.Ssl31SubmodulePaths;
import io.github.yellowhammer.designerxml.SchemaVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

class WriteProbeTest {
  @TempDir Path tempDir;

  @Test
  void probe() throws Exception {
    String relative = "src/cf/CommonForms/_ДемоМоиНастройки.xml";
    Path src = Ssl31SubmodulePaths.projectRoot().resolve(relative);
    Path copy = tempDir.resolve(src.getFileName());
    Files.copy(src, copy, StandardCopyOption.REPLACE_EXISTING);
    MdObjectPropertiesDto baseline = MdObjectPropertiesEdit.readDto(copy, SchemaVersion.V2_20);
    MdObjectPropertiesDto dto = MdObjectPropertiesEdit.readDto(copy, SchemaVersion.V2_20);
    dto.synonymRu = "Новый синоним";
    List<MdObjectPropertiesLeafDiff.GranularPatchChange> changes =
      MdObjectPropertiesLeafDiff.computePropertyChanges(baseline, dto);
    System.out.println("PROBE kind=" + baseline.kind + " changes=" + changes.size());
    for (MdObjectPropertiesLeafDiff.GranularPatchChange ch : changes) {
      System.out.println("PROBE change element=" + ch.mdElementLocalName());
    }
    try {
      MdObjectPropertiesEdit.writeDto(copy, SchemaVersion.V2_20, dto);
      System.out.println("PROBE write ok");
    } catch (Exception e) {
      System.out.println("PROBE write err: " + e.getMessage());
    }
  }
}
