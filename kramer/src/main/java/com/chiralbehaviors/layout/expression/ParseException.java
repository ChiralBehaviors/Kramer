/**
 * Copyright (c) 2016 Chiral Behaviors, LLC, all rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.chiralbehaviors.layout.expression;

/**
 * Thrown when an expression string cannot be parsed. Carries the character
 * offset in the input where the error was detected.
 *
 * @author hhildebrand
 */
public class ParseException extends Exception {

    private static final long serialVersionUID = 1L;

    private final int offset;

    public ParseException(String message, int offset) {
        super(message);
        this.offset = offset;
    }

    /** Character offset in the input string where the error was detected. */
    public int getOffset() {
        return offset;
    }
}
