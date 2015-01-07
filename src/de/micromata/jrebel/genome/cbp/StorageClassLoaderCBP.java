package de.micromata.jrebel.genome.cbp;

import org.zeroturnaround.bundled.javassist.CannotCompileException;
import org.zeroturnaround.bundled.javassist.ClassPool;
import org.zeroturnaround.bundled.javassist.CtClass;
import org.zeroturnaround.bundled.javassist.CtConstructor;
import org.zeroturnaround.bundled.javassist.CtNewMethod;
import org.zeroturnaround.bundled.javassist.NotFoundException;
import org.zeroturnaround.javarebel.ClassResourceSource;
import org.zeroturnaround.javarebel.integration.support.JavassistClassBytecodeProcessor;

/**
 * Patches de.micromata.genome.web.gwar.bootstrap.StorageClassLoader.
 */
public class StorageClassLoaderCBP extends JavassistClassBytecodeProcessor {

  private static final String LOGGER = "LoggerFactory.getLogger(\"Genome\")";

  @Override
  public void process(ClassPool cp, ClassLoader cl, CtClass ctClass) throws Exception {
    cp.importPackage("java.util");
    cp.importPackage("org.zeroturnaround.javarebel");
    cp.importPackage("org.zeroturnaround.javarebel.integration.util");
    cp.importPackage("de.micromata.genome.web.gwar.bootstrap");
    delegateResourceLoadingToJRebel(cp, ctClass);
  }

  private void delegateResourceLoadingToJRebel(ClassPool cp, CtClass ctClass) throws NotFoundException, CannotCompileException, Exception {
    ctClass.addInterface(cp.get(ClassResourceSource.class.getName()));
    implementClassResourceSource(ctClass);
    registerClassLoaderInConstructors(ctClass);
    patchResourceFinderLogic(ctClass);
  }

  private void registerClassLoaderInConstructors(CtClass ctClass) throws CannotCompileException {
    CtConstructor[] cs = ctClass.getDeclaredConstructors();
    for (int i = 0; i < cs.length; i++) {
      //register only if constructor calls super constructor -- to avoid double registration
      if (cs[i].callsSuper()) {
        cs[i].insertAfter("IntegrationFactory.getInstance().registerClassLoader(this, this);");
      }
    }
  }

  private void patchResourceFinderLogic(CtClass ctClass) throws Exception {
    ctClass.getDeclaredMethod("buildUrl").insertBefore(
        "{" +
        "  String localName = $2;" +
        "  Integration integration = IntegrationFactory.getInstance();" +
        "  if (integration.isResourceReplaced(this, localName)) {" +
        "    " + LOGGER + ".debug(\"Finding managed resource {}\", localName);" +
        "    return integration.findResource(this, localName);" +
        "  }" +
        "  else {" +
        "    " + LOGGER + ".debug(\"Finding non-managed resource {}\", localName);" +
        "  }" +
        "}");
  }

  private void implementClassResourceSource(CtClass ctClass) throws CannotCompileException {
    ctClass.addMethod(CtNewMethod.make(
        "public Resource getClassResource(String className) {" +
        "  " + LOGGER + ".debug(\"getClassResource({})\", className);" +
        "  return ResourceUtil.getClassResource(this, className);" +
        "}", ctClass));

    ctClass.addMethod(CtNewMethod.make(
        "public Resource getLocalResource(String name) {" +
        "  " + LOGGER + ".debug(\"getLocalResource({})\", className);" +
        "  for (ClassResPath rsm : resPaths) {" +
        "    StorageClassLoaderResPath rsp = rsm.getEntries().get(name);" +
        "    if (rsp != null) {" +
        "      " + LOGGER + ".debug(\"getLocalResource({}) found a path!\", className);" +
        "      return ResourceUtil.asResource(buildUrl(rsm, name, rsp.getLoader()));" +
        "    }" +
        "  }" +
        "  return null;" +
        "}", ctClass));

    ctClass.addMethod(CtNewMethod.make(
        "public Resource[] getLocalResources(String name) {" +
        "  " + LOGGER + ".debug(\"getLocalResources({})\", className);" +
        "  List resources = new ArrayList();" +
        "  for (ClassResPath rsm : resPaths) {" +
        "    StorageClassLoaderResPath rsp = rsm.getEntries().get(name);" +
        "    if (rsp != null) {" +
        "      " + LOGGER + ".debug(\"getLocalResources({}) found a path!\", className);" +
        "      resources.add(ResourceUtil.asResource(buildUrl(rsm, name, rsp.getLoader())));" +
        "    }" +
        "  }" +
        "  return resources.toArray(new Resource[resources.size()]);" +
        "}", ctClass));
  }

}
