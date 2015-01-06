package de.micromata.jrebel.genome;

import org.zeroturnaround.javarebel.ClassResourceSource;
import org.zeroturnaround.javarebel.Integration;
import org.zeroturnaround.javarebel.IntegrationFactory;
import org.zeroturnaround.javarebel.Plugin;

import de.micromata.jrebel.genome.cbp.StorageClassLoaderCBP;

public class GenomePlugin implements Plugin {

  public void preinit() {
    ClassLoader cl = getClass().getClassLoader();
    Integration integration = IntegrationFactory.getInstance();

    integration.addIntegrationProcessor(cl,
        "de.micromata.genome.web.gwar.bootstrap.StorageClassLoader",
        new StorageClassLoaderCBP());
  }

  public boolean checkDependencies(ClassLoader cl, ClassResourceSource crs) {
    return crs.getClassResource("de.micromata.genome.web.gwar.bootstrap.StorageClassLoader") != null;
  }

  public String getDescription() {
    return "<li>Enables reloading of Java classes in GWAR apps</li>";
  }

  public String getId() {
    return "genome_plugin";
  }

  public String getName() {
    return "Genome plugin";
  }

  public String getWebsite() {
    return "https://github.com/laubfall/genome-jrebel-plugin";
  }

  public String getAuthor() {
    return null;
  }

  public String getSupportedVersions() {
    return null;
  }

  public String getTestedVersions() {
    return null;
  }

}
