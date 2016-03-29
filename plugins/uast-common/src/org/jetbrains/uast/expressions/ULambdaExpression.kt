/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.uast

interface ULambdaExpression : UExpression {
    val valueParameters: List<UVariable>
    val body: UExpression

    override fun traverse(callback: UastCallback) {
        valueParameters.handleTraverseList(callback)
        body.handleTraverse(callback)
    }

    override fun logString() = log("ULambdaExpression", valueParameters, body)
    override fun renderString(): String {
        val renderedValueParameters = if (valueParameters.isEmpty())
            ""
        else
            valueParameters.joinToString { it.renderString() } + " ->\n"

        return "{ " + renderedValueParameters + body.renderString().withMargin + "\n}"
    }
}
