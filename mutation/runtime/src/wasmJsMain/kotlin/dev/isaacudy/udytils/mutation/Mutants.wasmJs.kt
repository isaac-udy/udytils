package dev.isaacudy.udytils.mutation

internal actual fun readMutantSetting(): String? = js("""(function(){try{if(typeof process!=="undefined"&&process.env&&process.env.UDYTILS_MUTANT)return String(process.env.UDYTILS_MUTANT);}catch(e){}try{var k=globalThis.__karma__;if(k&&k.config&&k.config.udytilsMutant)return String(k.config.udytilsMutant);}catch(e){}return null;})()""")
