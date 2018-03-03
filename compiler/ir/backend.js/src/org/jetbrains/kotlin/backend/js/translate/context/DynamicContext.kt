/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

/*
 * Copyright 2010-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.backend.js.translate.context

import org.jetbrains.kotlin.js.backend.ast.*
import org.jetbrains.kotlin.js.backend.ast.metadata.*

//TODO: consider renaming to scoping context
class DynamicContext private constructor(val scope: JsScope, private val currentBlock: JsBlock) {

    private var vars: JsVars? = null

    fun innerBlock(block: JsBlock): DynamicContext {
        return DynamicContext(scope, block)
    }

    fun declareTemporary(initExpression: JsExpression?, sourceInfo: Any?): TemporaryVariable {
        if (vars == null) {
            vars = JsVars().apply {
                synthetic = true
                source = sourceInfo
                currentBlock.statements.add(this)
            }
        }

        val temporaryName = JsScope.declareTemporary()

        vars!!.add(JsVars.JsVar(temporaryName, null).apply {
            synthetic = true
            source = if (initExpression != null) initExpression.source else sourceInfo
        })

        return TemporaryVariable.create(temporaryName, initExpression)
    }

    fun moveVarsFrom(dynamicContext: DynamicContext) {
        if (dynamicContext.vars != null) {
            if (vars == null) {
                vars = dynamicContext.vars
                currentBlock.statements.add(vars)
            } else {
                vars!!.addAll(dynamicContext.vars)
            }
            dynamicContext.currentBlock.statements.remove(dynamicContext.vars)
            dynamicContext.vars = null
        }
    }

    fun jsBlock(): JsBlock {
        return currentBlock
    }

    companion object {
        @JvmStatic
        fun rootContext(rootScope: JsScope, globalBlock: JsBlock): DynamicContext {
            return DynamicContext(rootScope, globalBlock)
        }

        @JvmStatic
        fun newContext(scope: JsScope, block: JsBlock): DynamicContext {
            return DynamicContext(scope, block)
        }
    }
}