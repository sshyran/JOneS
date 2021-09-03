/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.github.otymko.jos.compiler;

import com.github.otymko.jos.TestHelper;
import org.junit.jupiter.api.Test;

class NativeGlobalMethodTest {

  @Test
  void testUpperCase() throws Exception {
    var code = "Сообщить(ВРег(\"значение\"));";
    var model = "ЗНАЧЕНИЕ";
    TestHelper.checkCode(code, model);
  }

  @Test
  void testLowerCase() throws Exception {
    var code = "Сообщить(НРег(\"ЗнаЧение\"));";
    var model = "значение";
    TestHelper.checkCode(code, model);
  }

}
