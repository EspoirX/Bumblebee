@file:JvmName("TypeUtils")

package com.espoir.bumblebeecode.code.utils

import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

fun Type.getRawType(): Class<*> = com.espoir.bumblebeecode.code.utils.Utils.getRawType(this)

fun Type.hasUnresolvableType(): Boolean = com.espoir.bumblebeecode.code.utils.Utils.hasUnresolvableType(this)

fun ParameterizedType.getParameterUpperBound(index: Int): Type =
    com.espoir.bumblebeecode.code.utils.Utils.getParameterUpperBound(index, this)
