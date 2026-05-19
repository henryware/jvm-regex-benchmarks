package worldofregex

import scala.jdk.CollectionConverters.*

/** Derive a library's version from one of its classes, without hardcoding the
 *  number a second time.  Tries, in order:
 *    1. jar filename trailing "-X.Y.Z.jar"     (matches Maven coordinates,
 *                                               also covers our local jars)
 *    2. META-INF/maven/<g>/<a>/pom.properties  (Maven-built jars; can lag
 *                                               filename if the dev forgot
 *                                               to bump it — e.g. Florian)
 *    3. MANIFEST.MF  Implementation-Version    (a few jars)
 *  Returns "" if none yield a version (e.g. KMY's jint.jar, which carries
 *  no version metadata anywhere).
 *
 *  ProtectionDomain.CodeSource is empty under scala-cli's REPL classloader,
 *  so we resolve the jar via ClassLoader.getResource(<class.class>) instead,
 *  which yields a "jar:file:/path/to/foo.jar!/..." URL we can parse.
 */
object LibraryVersion {

    def fromClass(cls: Class[?]): String = locateJar(cls) match {
        case None => ""
        case Some(jarFile) =>
            val fromName = "-(\\d[\\w.-]*)\\.jar$".r
                .findFirstMatchIn(jarFile.getName).map(_.group(1))
            fromName.getOrElse(versionFromJarMetadata(jarFile))
    }

    private def versionFromJarMetadata(jarFile: java.io.File): String = {
        val jar = new java.util.jar.JarFile(jarFile)
        try {
            val pomVersion =
                jar.entries.asScala
                    .find { e =>
                        val n = e.getName
                        n.startsWith("META-INF/maven/") && n.endsWith("/pom.properties")
                    }
                    .flatMap { e =>
                        val props = new java.util.Properties()
                        val is = jar.getInputStream(e)
                        try props.load(is) finally is.close()
                        Option(props.getProperty("version")).filter(_.nonEmpty)
                    }
            val manifestVersion = Option(jar.getManifest)
                .flatMap(m => Option(m.getMainAttributes.getValue("Implementation-Version")))
                .filter(_.nonEmpty)
            pomVersion.orElse(manifestVersion).getOrElse("")
        } finally jar.close()
    }

    private def locateJar(cls: Class[?]): Option[java.io.File] = {
        val resName = cls.getName.replace('.', '/') + ".class"
        Option(cls.getClassLoader.getResource(resName)).flatMap { url =>
            val s = url.toString
            val bang = s.indexOf("!")
            if (s.startsWith("jar:file:") && bang > "jar:file:".length) {
                val pathPart = s.substring("jar:file:".length, bang)
                Some(new java.io.File(java.net.URLDecoder.decode(pathPart, "UTF-8")))
            } else None
        }
    }
}
