package de.micromata.jrebel.genome.cbp;

import org.zeroturnaround.bundled.javassist.CannotCompileException;
import org.zeroturnaround.bundled.javassist.ClassPool;
import org.zeroturnaround.bundled.javassist.CtClass;
import org.zeroturnaround.bundled.javassist.CtConstructor;
import org.zeroturnaround.bundled.javassist.CtNewMethod;
import org.zeroturnaround.bundled.javassist.NotFoundException;
import org.zeroturnaround.javarebel.ClassEventListener;
import org.zeroturnaround.javarebel.ClassResourceSource;
import org.zeroturnaround.javarebel.integration.support.JavassistClassBytecodeProcessor;

/**
 * Patches de.micromata.genome.web.gwar.bootstrap.StorageClassLoader.
 */
public class StorageClassLoaderCBP extends JavassistClassBytecodeProcessor {

  private static final String BUILD_URL_ORIGINAL = "__jrBuildUrl";
  private static final String LOGGER = "LoggerFactory.getLogger(\"Genome\")";

  @Override
  public void process(ClassPool cp, ClassLoader cl, CtClass ctClass) throws Exception {
    cp.importPackage("java.util");
    cp.importPackage("org.zeroturnaround.javarebel");
    cp.importPackage("org.zeroturnaround.javarebel.integration.util");
    cp.importPackage("de.micromata.genome.web.gwar.bootstrap");
    delegateResourceLoadingToJRebel(cp, ctClass);
    clearInternalCacheWhenClassesAreReloaded(cp, ctClass);
  }

  private void delegateResourceLoadingToJRebel(ClassPool cp, CtClass ctClass) throws NotFoundException, CannotCompileException, Exception {
    ctClass.addInterface(cp.get(ClassResourceSource.class.getName()));
    implementClassResourceSource(ctClass);
    registerClassLoaderInConstructors(ctClass);
    patchResourceFinderLogic(ctClass);
  }

  private void clearInternalCacheWhenClassesAreReloaded(ClassPool cp, CtClass ctClass) throws NotFoundException, Exception {
    ctClass.addInterface(cp.get(ClassEventListener.class.getName()));
    implementClassEventListener(ctClass);
    registerClassEventListenerInConstructors(ctClass);
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
    //rename original implementation
    ctClass.getDeclaredMethod("buildUrl").setName(BUILD_URL_ORIGINAL);
    //add our own implementation in its place
    ctClass.addMethod(CtNewMethod.make(
        "protected URL buildUrl(ClassResPath rsm, String localName, StorageClassLoaderResPath.ResLoader loader) {" +
        "  Integration integration = IntegrationFactory.getInstance();" +
        "  if (integration.isResourceReplaced(this, localName)) {" +
        "    " + LOGGER + ".debug(\"Finding managed resource {}\", localName);" +
        "    $_ = integration.findResource(this, localName);" +
        "  }" +
        "  else {" +
        "    " + LOGGER + ".debug(\"Finding non-managed resource {}\", localName);" +
        "    $_ = $proceed($$);" +
        "  }" +
        "}", ctClass));
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
        "      return ResourceUtil.asResource(" + BUILD_URL_ORIGINAL + "(rsm, name, rsp.getLoader()));" +
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
        "      resources.add(ResourceUtil.asResource(" + BUILD_URL_ORIGINAL + "(rsm, name, rsp.getLoader())));" +
        "    }" +
        "  }" +
        "  return resources.toArray(new Resource[resources.size()]);" +
        "}", ctClass));
  }

  private void registerClassEventListenerInConstructors(CtClass ctClass) throws Exception {
    CtConstructor[] cs = ctClass.getDeclaredConstructors();
    for (int i = 0; i < cs.length; i++) {
      //register only if constructor calls super constructor -- to avoid double registration
      if (cs[i].callsSuper()) {
        cs[i].insertAfter("ReloaderFactory.getInstance().addClassReloadListener(ClassEventListenerUtil.bindContextClassLoader(this));");
      }
    }
  }

  private void implementClassEventListener(CtClass ctClass) throws Exception {
    ctClass.addMethod(CtNewMethod.make(
        "public void onClassEvent(int eventType, Class klass) {" +
        "  String className = klass.getName();" +
        "  loadedClasses.remove(className);" +
        "  missedClasses.remove(className);" +
        "  missedResources.remove(className.replace('.', '/') + \".class\");" +
        "  " + LOGGER + ".debug(\"cleared internal cache for {}\", className);" +
        "}", ctClass));
    ctClass.addMethod(CtNewMethod.make(
        "public int priority() {" +
        "  return ClassEventListener.PRIORITY_CORE;" +
        "}", ctClass));
  }

}
