package worldofregex.macros

import com.justinblank.strings.Pattern

object Needle {
    inline def compile(inline regex: String): Pattern =
        ${ NeedleMacro.compileImpl('regex) }
}
