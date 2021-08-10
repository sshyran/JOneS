/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.github.otymko.jos.runtime.context.type.primitive;

import com.github.otymko.jos.exception.MachineException;
import com.github.otymko.jos.runtime.context.ContextClass;
import com.github.otymko.jos.runtime.context.IValue;
import com.github.otymko.jos.runtime.context.type.DataType;
import com.github.otymko.jos.runtime.context.type.PrimitiveValue;
import com.github.otymko.jos.runtime.context.type.ValueFactory;
import com.github.otymko.jos.runtime.machine.info.ContextInfo;

import java.util.function.Predicate;

@ContextClass(name = "Булево", alias = "Boolean")
public class BooleanValue extends PrimitiveValue {
  public static final ContextInfo INFO = ContextInfo.createByClass(BooleanValue.class);

  public static final BooleanValue TRUE = new BooleanValue(true);
  public static final BooleanValue FALSE = new BooleanValue(false);

  private static final Predicate<String> IS_TRUE = view -> view.equalsIgnoreCase("истина")
    || view.equalsIgnoreCase("true") || view.equalsIgnoreCase("да");
  private static final Predicate<String> IS_FALSE = view -> view.equalsIgnoreCase("ложь")
    || view.equalsIgnoreCase("false") || view.equalsIgnoreCase("нет");

  private final boolean value;

  private BooleanValue(boolean value) {
    this.value = value;
    setDataType(DataType.BOOLEAN);
  }

  public static IValue parse(String view) {
    IValue result;
    if (IS_TRUE.test(view)) {
      result = ValueFactory.create(true);
    } else if (IS_FALSE.test(view)) {
      result = ValueFactory.create(false);
    } else {
      throw MachineException.convertToBooleanException();
    }
    return result;
  }

  @Override
  public ContextInfo getContextInfo() {
    return INFO;
  }

  @Override
  public boolean asBoolean() {
    return value;
  }

  @Override
  public float asNumber() {
    if (value) {
      return 1;
    }
    return 0;
  }

  @Override
  public String asString() {
    return value ? "Да" : "Нет";
  }

  // FIXME
  @Override
  public int compareTo(IValue other) {
    if (other.getDataType() == DataType.BOOLEAN || other.getDataType() == DataType.NUMBER) {
      return Float.compare(asNumber(), other.asNumber());
    }
    return super.compareTo(other);
  }

}
