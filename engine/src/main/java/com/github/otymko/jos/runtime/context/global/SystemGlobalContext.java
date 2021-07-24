package com.github.otymko.jos.runtime.context.global;

import com.github.otymko.jos.runtime.IValue;
import com.github.otymko.jos.runtime.context.AttachableContext;
import com.github.otymko.jos.runtime.context.label.ContextMethod;

public class SystemGlobalContext extends AttachableContext {

  public SystemGlobalContext() {
    // none
  }

  @ContextMethod(name = "Сообщить", alias = "Message")
  public static void message(IValue message) {
    System.out.println(message.asString());
  }

}
