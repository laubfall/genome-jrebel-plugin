package de.micromata.jrebel.genome.cbp;

import org.zeroturnaround.bundled.javassist.CannotCompileException;
import org.zeroturnaround.bundled.javassist.ClassPool;
import org.zeroturnaround.bundled.javassist.CtClass;
import org.zeroturnaround.bundled.javassist.CtConstructor;
import org.zeroturnaround.bundled.javassist.CtMethod;
import org.zeroturnaround.bundled.javassist.CtNewMethod;
import org.zeroturnaround.bundled.javassist.NotFoundException;
import org.zeroturnaround.javarebel.ClassResourceSource;
import org.zeroturnaround.javarebel.IntegrationFactory;
import org.zeroturnaround.javarebel.integration.support.JavassistClassBytecodeProcessor;
import org.zeroturnaround.javarebel.integration.util.ResourceUtil;

/**
 * Patches de.micromata.genome.web.gwar.bootstrap.StorageClassLoader.
 */
public class StorageClassLoaderCBP extends JavassistClassBytecodeProcessor {

  @Override
  public void process(ClassPool cp, ClassLoader cl, CtClass ctClass) throws Exception {
    cp.importPackage("java.util");
    cp.importPackage("org.zeroturnaround.javarebel");
    cp.importPackage("org.zeroturnaround.javarebel.gen");
    cp.importPackage("org.zeroturnaround.javarebel.integration.util");
    cp.importPackage("de.micromata.genome.web.gwar.bootstrap");
    delegateResourceLoadingToJRebel(cp, ctClass);
  }

  private void delegateResourceLoadingToJRebel(ClassPool cp, CtClass ctClass) throws NotFoundException, CannotCompileException, Exception {
    ctClass.addInterface(cp.get(ClassResourceSource.class.getName()));
    implementClassResourceSource(ctClass);
    registerClassLoaderInConstructors(ctClass);
    whenClassPathChangesReInitializeIntegration(ctClass);
    patchClassLoadingLogic(ctClass);
    patchResourceLoadingLogic(ctClass);
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

  private void whenClassPathChangesReInitializeIntegration(CtClass ctClass) throws Exception {
    String reInit = "IntegrationFactory.getInstance().reinitializeClassLoader(this);";
    ctClass.getDeclaredMethod("addJar").insertAfter(reInit);
    ctClass.getDeclaredMethod("addClassPath").insertAfter(reInit);
  }

  private void patchResourceLoadingLogic(CtClass ctClass) throws Exception {
    {
      CtMethod method = ctClass.getDeclaredMethod("getResourceLocally"); 
      method.insertBefore(
        "Integration integration = IntegrationFactory.getInstance();" +
        "if (integration.isResourceReplaced($0, $1)) {" +
        "    return integration.findResource($0, $1);" +
        "}"
      );
    }
    {
      CtMethod method = ctClass.getDeclaredMethod("getResourceListLocally");
      method.insertBefore(
        "Integration integration = IntegrationFactory.getInstance();" +
        "if (integration.isResourceReplaced($0, $1)) {" +
        "  Enumeration en = integration.findResources($0, $1);" +
        "  if (en != null) {" +
        "    return Collections.list(en);" +
        "  }" +
        "}" 
      );
    }
  }

  private void patchClassLoadingLogic(CtClass ctClass) throws Exception {
    CtMethod findClassMethod = ctClass.getDeclaredMethod("loadClassLocally"); 
    findClassMethod.insertBefore(
      "{ synchronized ($0) {" + 
      "    Class result =" + 
      "      $0.findLoadedClass($1);" + 
      "    if (result != null)" + 
      "      return result;" + 
      "    result = " + 
      "      IntegrationFactory.getInstance().findReloadableClass($0, $1);" + 
      "    if (result != null)" + 
      "      return result;" + 
      "}}"
    );
  }

  private void implementClassResourceSource(CtClass ctClass) throws CannotCompileException {
    ctClass.addMethod(CtNewMethod.make(
        "public Resource getLocalResource(String name) {" +
        "  return ResourceUtil.asResource(getResourceLocally(name, false));" +
        "}", ctClass));

    ctClass.addMethod(CtNewMethod.make(
        "public Resource[] getLocalResources(String name) {" +
        "  return ResourceUtil.asResources(getResourceListLocally(name));" +
        "}", ctClass));

    ctClass.addMethod(CtNewMethod.make(
        "public Resource getClassResource(String className) {" +
        "  return getLocalResource(className.replace('.', '/') + \".class\");" +
        "}", ctClass));
  }

}
