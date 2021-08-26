/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.github.otymko.jos.compiler;

import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLParserBaseVisitor;
import com.github._1c_syntax.bsl.parser.BSLParserRuleContext;
import com.github.otymko.jos.exception.CompilerException;
import com.github.otymko.jos.module.ModuleImageCache;
import com.github.otymko.jos.runtime.context.type.ValueFactory;
import com.github.otymko.jos.runtime.machine.Command;
import com.github.otymko.jos.runtime.machine.OperationCode;
import com.github.otymko.jos.runtime.machine.info.MethodInfo;
import com.github.otymko.jos.runtime.machine.info.ParameterInfo;
import com.github.otymko.jos.runtime.machine.info.VariableInfo;
import com.github.otymko.jos.util.StringLineCleaner;
import lombok.Data;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/**
 * Компилятор в опкод
 */
public class Compiler extends BSLParserBaseVisitor<ParseTree> {
  private static final String ENTRY_METHOD_NAME = "$entry";
  private static final Integer DUMMY_ADDRESS = -1;

  private final ScriptCompiler compiler;
  private final ModuleImageCache imageCache;
  private final List<Integer> currentCommandReturnInMethod = new ArrayList<>();
  private final Deque<Compiler.NestedLoopInfo> nestedLoops = new ArrayDeque<>();

  private MethodDescriptor currentMethodDescriptor;
  private SymbolScope localScope;

  @Data
  private static class NestedLoopInfo {
    private int startPoint = DUMMY_ADDRESS;
    private List<Integer> breakStatements = new ArrayList<>();
    private int tryNesting = 0;

    private NestedLoopInfo() {
      // none
    }

    public static Compiler.NestedLoopInfo create() {
      return new Compiler.NestedLoopInfo();
    }

    public static Compiler.NestedLoopInfo create(int startIndex) {
      var loop = new Compiler.NestedLoopInfo();
      loop.setStartPoint(startIndex);
      return loop;
    }
  }

  public Compiler(ModuleImageCache imageCache, ScriptCompiler compiler) {
    this.imageCache = imageCache;
    this.compiler = compiler;
  }

  @Override
  public ParseTree visitFile(BSLParser.FileContext fileContext) {
    var moduleScope = compiler.getModuleContext().getScopes().get(0);

    var subsContext = fileContext.subs();
    if (subsContext != null) {
      var subs = subsContext.sub();
      for (var sub : subs) {
        var method = createMethodDescriptor(sub);
        // TODO:
        imageCache.getMethods().add(method);
        moduleScope.getMethods().add(method.getSignature());

        var index = moduleScope.getMethods().indexOf(method.getSignature());
        moduleScope.getMethodNumbers().put(method.getSignature().getName().toUpperCase(Locale.ENGLISH), index);
      }
    }

    if (fileContext.fileCodeBlock() != null && !fileContext.fileCodeBlock().codeBlock().statement().isEmpty()) {
      var method = createMethodDescriptorFromBody();
      // TODO:
      imageCache.getMethods().add(method);
      moduleScope.getMethods().add(method.getSignature());

      var index = moduleScope.getMethods().indexOf(method.getSignature());
      moduleScope.getMethodNumbers().put(method.getSignature().getName().toUpperCase(Locale.ENGLISH), index);
    }

    return super.visitFile(fileContext);
  }

  @Override
  public ParseTree visitModuleVar(BSLParser.ModuleVarContext moduleVarContext) {
    // TODO: обработка аннотаций

    var variables = moduleVarContext.moduleVarsList().moduleVarDeclaration();
    for (var variable : variables) {
      var name = variable.var_name().getText();
      var variableInfo = new VariableInfo(name);
      compiler.getModuleContext().defineVariable(variableInfo);
      imageCache.getVariables().add(variableInfo);
    }

    return moduleVarContext;
  }

  @Override
  public ParseTree visitProcedure(BSLParser.ProcedureContext procedure) {
    var methodName = getMethodName(procedure);
    prepareModuleMethod(methodName, false);
    super.visitProcedure(procedure);
    processMethodEnd(false);
    pruneContextModuleMethod();
    return procedure;
  }

  @Override
  public ParseTree visitFunction(BSLParser.FunctionContext function) {
    var methodName = getMethodName(function);
    prepareModuleMethod(methodName, false);
    super.visitFunction(function);
    addHiddenReturnForMethod();
    processMethodEnd(false);
    pruneContextModuleMethod();
    return function;
  }

  @Override
  public ParseTree visitFileCodeBlock(BSLParser.FileCodeBlockContext fileCodeBlock) {
    // TODO
    if (fileCodeBlock.codeBlock().statement().isEmpty()) {
      return fileCodeBlock;
    }

    prepareModuleMethod(ENTRY_METHOD_NAME, true);
    super.visitFileCodeBlock(fileCodeBlock);
    processMethodEnd(true);
    pruneContextModuleMethod();
    return fileCodeBlock;
  }

  @Override
  public ParseTree visitParamList(BSLParser.ParamListContext paramList) {
    return paramList;
  }

  @Override
  public ParseTree visitSubVar(BSLParser.SubVarContext subVarContext) {
    // TODO: обработка аннотаций ?

    var variables = subVarContext.subVarsList().subVarDeclaration();
    for (var variable : variables) {
      var name = variable.var_name().getText();
      var variableInfo = new VariableInfo(name);
      currentMethodDescriptor.getVariables().add(variableInfo);

      localScope.getVariables().add(variableInfo);
      localScope.getVariableNumbers().put(name.toUpperCase(Locale.ENGLISH), localScope.getVariables().indexOf(variableInfo));
      // LoadLoc ?
    }

    return subVarContext;
  }

  @Override
  public ParseTree visitCodeBlock(BSLParser.CodeBlockContext codeBlock) {
    return super.visitCodeBlock(codeBlock);
  }

  @Override
  public ParseTree visitFileCodeBlockBeforeSub(BSLParser.FileCodeBlockBeforeSubContext fileCodeBlockBeforeSub) {
    // TODO: выкидывать ошибку, если есть код в codeblocks
    return fileCodeBlockBeforeSub;
  }

  @Override
  public ParseTree visitStatement(BSLParser.StatementContext statement) {
    int numberLint = statement.getStart().getLine();
    addCommand(OperationCode.LineNum, numberLint);
    if (currentMethodDescriptor != null && currentMethodDescriptor.getEntry() == DUMMY_ADDRESS) {
      currentMethodDescriptor.setEntry(imageCache.getCode().size() - 1);
    }
    return super.visitStatement(statement);
  }

  @Override
  public ParseTree visitAssignment(BSLParser.AssignmentContext assignment) {
    var lValue = assignment.lValue();
    var expression = assignment.expression();
    visitExpression(expression);

    var variableName = lValue.getText();
    var address = compiler.findVariableInContext(variableName);
    if (address != null) {

      if (address.getContextId() == compiler.getModuleContext().getMaxScopeIndex()) {
        addCommand(OperationCode.LoadLoc, address.getSymbolId());
      } else {
        addCommand(OperationCode.LoadVar, address.getSymbolId());
      }

    } else {

      var variableInfo = new VariableInfo(variableName);
      localScope.getVariables().add(variableInfo);
      localScope.getVariableNumbers().put(variableName.toUpperCase(Locale.ENGLISH), localScope.getVariables().indexOf(variableInfo));
      var index = localScope.getVariables().size() - 1;
      addCommand(OperationCode.LoadLoc, index);

    }

    return assignment;
  }

  @Override
  public ParseTree visitIfStatement(BSLParser.IfStatementContext ifStatement) {
    // TODO
    throw CompilerException.notImplementedException("ifStatement");
  }

  @Override
  public ParseTree visitWhileStatement(BSLParser.WhileStatementContext whileStatement) {
    // прыжок наверх цикла должен попадать на опкод LineNum
    // поэтому указываем адрес - 1
    var conditionIndex = imageCache.getCode().size() - 1;
    var loopRecord = Compiler.NestedLoopInfo.create(conditionIndex);

    nestedLoops.push(loopRecord);
    processExpression(whileStatement.expression(), new ArrayDeque<>());

    var jumpFalseIndex = addCommand(OperationCode.JmpFalse, DUMMY_ADDRESS);

    visitCodeBlock(whileStatement.codeBlock());

    addCommand(OperationCode.Jmp, conditionIndex);
    var endLoop = addCommand(OperationCode.Nop, 0);
    correctCommandArgument(jumpFalseIndex, endLoop);
    correctBreakStatements(nestedLoops.pop(), endLoop);

    return whileStatement;
  }

  @Override
  public ParseTree visitForStatement(BSLParser.ForStatementContext forStatement) {
    // TODO
    throw CompilerException.notImplementedException("forStatement");
  }

  @Override
  public ParseTree visitForEachStatement(BSLParser.ForEachStatementContext forEachStatement) {
    // TODO
    throw CompilerException.notImplementedException("forEachStatement");
  }

  @Override
  public ParseTree visitTryStatement(BSLParser.TryStatementContext tryStatement) {
    var beginTryIndex = addCommand(OperationCode.BeginTry, DUMMY_ADDRESS);
    visitTryCodeBlock(tryStatement.tryCodeBlock());
    var jmpIndex = addCommand(OperationCode.Jmp, DUMMY_ADDRESS);

    var beginHandler = addCommand(OperationCode.LineNum,
      tryStatement.exceptCodeBlock().getStart().getLine());

    correctCommandArgument(beginTryIndex, beginHandler);

    visitExceptCodeBlock(tryStatement.exceptCodeBlock());

    var endIndex = addCommand(OperationCode.LineNum, tryStatement.getStop().getLine());

    addCommand(OperationCode.EndTry, 0);
    correctCommandArgument(jmpIndex, endIndex);

    return tryStatement;
  }

  @Override
  public ParseTree visitTryCodeBlock(BSLParser.TryCodeBlockContext ctx) {
    pushTryNesting();
    super.visitTryCodeBlock(ctx);
    popTryNesting();
    return ctx;
  }

  @Override
  public ParseTree visitReturnStatement(BSLParser.ReturnStatementContext returnStatement) {
    super.visitReturnStatement(returnStatement);

    addCommand(OperationCode.MakeRawValue, 0);
    var indexJump = addCommand(OperationCode.Jmp, DUMMY_ADDRESS);
    currentCommandReturnInMethod.add(indexJump);

    return returnStatement;
  }

  @Override
  public ParseTree visitContinueStatement(BSLParser.ContinueStatementContext continueStatement) {
    exitTryBlocks();
    var loopInfo = nestedLoops.peek();
    // FIXME
    assert loopInfo != null;
    addCommand(OperationCode.Jmp, loopInfo.getStartPoint());
    return continueStatement;
  }

  @Override
  public ParseTree visitBreakStatement(BSLParser.BreakStatementContext breakStatement) {
    exitTryBlocks();
    var loopInfo = nestedLoops.peek();
    // FIXME
    assert loopInfo != null;
    var idx = addCommand(OperationCode.Jmp, DUMMY_ADDRESS);
    loopInfo.getBreakStatements().add(idx);
    return breakStatement;
  }

  @Override
  public ParseTree visitRaiseStatement(BSLParser.RaiseStatementContext raiseStatement) {

    var expression = raiseStatement.expression();
    if (expression == null) {
      var parent = raiseStatement.getParent();
      var isValidRaise = false;
      while (parent != null
        && parent.getRuleIndex() != BSLParser.RULE_subCodeBlock
        && parent.getRuleIndex() != BSLParser.RULE_fileCodeBlock) {

        if (parent.getRuleIndex() == BSLParser.RULE_exceptCodeBlock) {
          isValidRaise = true;
          break;
        }
      }

      if (!isValidRaise) {
        throw CompilerException.mismatchedRaiseExpression();
      }

      addCommand(OperationCode.RaiseException, -1);

    } else {
      visitExpression(expression);
      addCommand(OperationCode.RaiseException, 0);
    }

    return raiseStatement;
  }

  @Override
  public ParseTree visitExecuteStatement(BSLParser.ExecuteStatementContext executeStatement) {
    // TODO
    throw CompilerException.notImplementedException("executeStatement");
  }

  @Override
  public ParseTree visitGotoStatement(BSLParser.GotoStatementContext gotoStatement) {
    // TODO
    throw CompilerException.notImplementedException("gotoStatement");
  }

  @Override
  public ParseTree visitAddHandlerStatement(BSLParser.AddHandlerStatementContext addHandlerStatement) {
    // TODO
    throw CompilerException.notSupportedException();
  }

  @Override
  public ParseTree visitRemoveHandlerStatement(BSLParser.RemoveHandlerStatementContext removeHandlerStatement) {
    // TODO
    throw CompilerException.notSupportedException();
  }

  @Override
  public ParseTree visitWaitStatement(BSLParser.WaitStatementContext waitStatement) {
    // TODO
    throw CompilerException.notSupportedException();
  }

  @Override
  public ParseTree visitGlobalMethodCall(BSLParser.GlobalMethodCallContext globalMethodCall) {
    var callStatement = (BSLParser.CallStatementContext) globalMethodCall.getParent();

    var paramList = callStatement.globalMethodCall().doCall().callParamList();
    processParamList(paramList);
    addCommand(OperationCode.ArgNum, calcParams(paramList));
    processMethodCall(callStatement.globalMethodCall().methodName(), false);
    return globalMethodCall;
  }

  @Override
  public ParseTree visitAccessCall(BSLParser.AccessCallContext accessCall) {
    var callStatement = (BSLParser.CallStatementContext) accessCall.getParent();

    var identifier = callStatement.IDENTIFIER().getText();
    processIdentifier(identifier);
    processAccessCall(accessCall, false);
    return accessCall;
  }

  @Override
  public ParseTree visitExpression(BSLParser.ExpressionContext expression) {
    processExpression(expression, new ArrayDeque<>());
    return expression;
  }

  @Override
  public ParseTree visitConstValue(BSLParser.ConstValueContext constValue) {
    var constant = getConstantDefinitionByConstValue(constValue, false);
    if (!imageCache.getConstants().contains(constant)) {
      imageCache.getConstants().add(constant);
    }
    addCommand(OperationCode.PushConst, imageCache.getConstants().indexOf(constant));
    return constValue;
  }

  @Override
  public ParseTree visitComplexIdentifier(BSLParser.ComplexIdentifierContext complexIdentifier) {
    processComplexIdentifier(complexIdentifier);
    return complexIdentifier;
  }

  private MethodDescriptor createMethodDescriptor(BSLParser.SubContext subContext) {
    boolean isFunction;
    String methodName;
    BSLParser.ParamListContext parametersList;

    if (subContext.function() != null) {
      isFunction = true;
      methodName = getMethodName(subContext.function());
      parametersList = getMethodParamListContext(subContext.function());
    } else {
      isFunction = false;
      methodName = getMethodName(subContext.procedure());
      parametersList = getMethodParamListContext(subContext.procedure());
    }

    var parameterInfos = buildParameterInfos(parametersList);
    var methodInfo = new MethodInfo(methodName, methodName, isFunction, parameterInfos);

    var methodDescriptor = new MethodDescriptor();
    methodDescriptor.setSignature(methodInfo);
    return methodDescriptor;
  }

  private MethodDescriptor createMethodDescriptorFromBody() {
    var methodInfo = new MethodInfo(ENTRY_METHOD_NAME, ENTRY_METHOD_NAME, false, new ParameterInfo[0]);

    var methodDescriptor = new MethodDescriptor();
    methodDescriptor.setSignature(methodInfo);
    return methodDescriptor;
  }

  private String getMethodName(BSLParser.FunctionContext function) {
    return function.funcDeclaration().subName().getText();
  }

  private String getMethodName(BSLParser.ProcedureContext procedure) {
    return procedure.procDeclaration().subName().getText();
  }

  private BSLParser.ParamListContext getMethodParamListContext(BSLParser.FunctionContext function) {
    return function.funcDeclaration().paramList();
  }

  private BSLParser.ParamListContext getMethodParamListContext(BSLParser.ProcedureContext procedure) {
    return procedure.procDeclaration().paramList();
  }

  private ParameterInfo[] buildParameterInfos(BSLParser.ParamListContext paramList) {
    ParameterInfo[] parameterInfos;
    if (paramList != null) {
      int index = 0;
      parameterInfos = new ParameterInfo[paramList.param().size()];
      for (var param : paramList.param()) {
        var parameterName = param.IDENTIFIER().toString();

        var builder = ParameterInfo.builder()
          .name(parameterName)
          .byValue(param.VAL_KEYWORD() != null);

        if (param.defaultValue() != null) {
          var constant = getConstantDefinitionByConstValue(param.defaultValue().constValue(), true);
          imageCache.getConstants().add(constant);
          var indexConstant = imageCache.getConstants().indexOf(constant);
          addCommand(OperationCode.PushConst, indexConstant);

          builder.hasDefaultValue(true);
          builder.defaultValueIndex(indexConstant);
        }

        parameterInfos[index] = builder.build();
        index++;
      }
    } else {
      parameterInfos = new ParameterInfo[0];
    }
    return parameterInfos;
  }

  private MethodDescriptor getMethodDescriptor(String name) {
    // TODO: поиск в локальной мапе

    //noinspection OptionalGetWithoutIsPresent
    return imageCache.getMethods().stream()
      .filter(methodDescriptor -> methodDescriptor.getSignature().getName().equals(name))
      .findAny()
      .get();
  }

  private int addCommand(OperationCode operationCode, int argument) {
    var index = imageCache.getCode().size();
    imageCache.getCode().add(new Command(operationCode, argument));
    return index;
  }

  private int addReturn() {
    return addCommand(OperationCode.Return, 0);
  }

  private void processExpression(BSLParser.ExpressionContext expression, Deque<ExpressionOperator> operators) {
    var booleanExpression = false;
    List<Integer> booleanCommands = new ArrayList<>();
    for (var index = 0; index < expression.getChildCount(); index++) {
      var child = (BSLParserRuleContext) expression.getChild(index);
      if (child.getRuleIndex() == BSLParser.RULE_member) {
        processMember((BSLParser.MemberContext) child, operators);
      } else if (child.getRuleIndex() == BSLParser.RULE_operation) {
        var operation = getOperatorByChild((BSLParser.OperationContext) child);
        if (isLogicOperator(operation)) {
          booleanExpression = true;
          var indexCommand = addOperator(operation);
          booleanCommands.add(indexCommand);
        } else {
          processOperator(operation, operators);
        }
      } else {
        throw CompilerException.notSupportedException();
      }
    }

    while (!operators.isEmpty()) {
      var indexCommand = addOperator(operators.pop());
      booleanCommands.add(indexCommand);
    }

    if (booleanExpression) {
      var lastIndexCommand = addCommand(OperationCode.MakeBool, 0);
      booleanCommands.forEach(commandId -> correctCommandArgument(commandId, lastIndexCommand));
    }

  }

  private ExpressionOperator getOperatorByChild(BSLParser.OperationContext operationContext) {
    ExpressionOperator operator;
    if (operationContext.PLUS() != null) {
      operator = ExpressionOperator.ADD;
    } else if (operationContext.MINUS() != null) {
      operator = ExpressionOperator.SUB;
    } else if (operationContext.MUL() != null) {
      operator = ExpressionOperator.MUL;
    } else if (operationContext.QUOTIENT() != null) {
      operator = ExpressionOperator.DIV;
    } else if (operationContext.boolOperation() != null) {
      var bool = operationContext.boolOperation();
      if (bool.AND_KEYWORD() != null) {
        operator = ExpressionOperator.AND;
      } else if (bool.OR_KEYWORD() != null) {
        operator = ExpressionOperator.OR;
      } else {
        throw CompilerException.notNotSupportedExpressionOperator(bool.getText());
      }
    } else if (operationContext.compareOperation() != null) {
      var compare = operationContext.compareOperation();
      if (compare.ASSIGN() != null) {
        operator = ExpressionOperator.EQUAL;
      } else if (compare.LESS() != null) {
        operator = ExpressionOperator.LESS;
      } else if (compare.LESS_OR_EQUAL() != null) {
        operator = ExpressionOperator.LESS_OR_EQUAL;
      } else if (compare.GREATER() != null) {
        operator = ExpressionOperator.GREATER;
      } else if (compare.GREATER_OR_EQUAL() != null) {
        operator = ExpressionOperator.GREATER_OR_EQUAL;
      } else if (compare.NOT_EQUAL() != null) {
        operator = ExpressionOperator.NOT_EQUAL;
      } else {
        throw CompilerException.notNotSupportedExpressionOperator(compare.getText());
      }
    } else {
      throw CompilerException.notNotSupportedExpressionOperator(operationContext.getText());
    }
    return operator;
  }

  private void processMember(BSLParser.MemberContext memberContext, Deque<ExpressionOperator> operators) {
    if (memberContext.constValue() != null) {
      visitConstValue(memberContext.constValue());
    } else if (memberContext.expression() != null) {
      // тут скобки `(` \ `)`
      visitExpression(memberContext.expression());
    } else if (memberContext.complexIdentifier() != null) {
      processComplexIdentifier(memberContext.complexIdentifier());
    } else {
      throw CompilerException.notSupportedException();
    }

    if (memberContext.unaryModifier() != null) {
      processUnaryModifier(memberContext.unaryModifier(), operators);
    }

  }

  private int addOperator(ExpressionOperator operator) {
    OperationCode operationCode;
    switch (operator) {
      case ADD:
        operationCode = OperationCode.Add;
        break;
      case SUB:
        operationCode = OperationCode.Sub;
        break;
      case MUL:
        operationCode = OperationCode.Mul;
        break;
      case DIV:
        operationCode = OperationCode.Div;
        break;
      case UNARY_PLUS:
        operationCode = OperationCode.Number;
        break;
      case UNARY_MINUS:
        operationCode = OperationCode.Neg;
        break;
      case NOT:
        operationCode = OperationCode.Not;
        break;
      case OR:
        operationCode = OperationCode.Or;
        break;
      case AND:
        operationCode = OperationCode.And;
        break;
      case EQUAL:
        operationCode = OperationCode.Equals;
        break;
      case LESS:
        operationCode = OperationCode.Less;
        break;
      case LESS_OR_EQUAL:
        operationCode = OperationCode.LessOrEqual;
        break;
      case GREATER:
        operationCode = OperationCode.Greater;
        break;
      case GREATER_OR_EQUAL:
        operationCode = OperationCode.GreaterOrEqual;
        break;
      case NOT_EQUAL:
        operationCode = OperationCode.NotEqual;
        break;
      default:
        throw CompilerException.notNotSupportedExpressionOperator(operator.getText());
    }
    return addCommand(operationCode, 0);
  }

  private boolean isLogicOperator(ExpressionOperator operator) {
    return operator == ExpressionOperator.OR || operator == ExpressionOperator.AND;
  }

  private void correctCommandArgument(int index, int newValue) {
    var cmd = imageCache.getCode().get(index);
    cmd.setArgument(newValue);
  }

  private void processOperator(ExpressionOperator operation, Deque<ExpressionOperator> operators) {
    if (!operators.isEmpty()) {
      ExpressionOperator operatorFromDeque;
      while (!operators.isEmpty()) {
        operatorFromDeque = operators.peek();
        if (operatorFromDeque.getPriority() >= operation.getPriority()) {
          addOperator(operators.pop());
        } else {
          break;
        }
      }
    }
    operators.push(operation);
  }

  private ConstantDefinition getConstantDefinitionByConstValue(BSLParser.ConstValueContext constValue,
                                                               boolean isDefaultValue) {
    ConstantDefinition constant;
    if (constValue.string() != null) {
      var value = constValue.string().getText();
      value = StringLineCleaner.clean(value);
      constant = new ConstantDefinition(ValueFactory.create(value));
    } else if (constValue.numeric() != null) {
      var value = Integer.parseInt(constValue.numeric().getText());
      if (isDefaultValue) {
        if (constValue.MINUS() != null) {
          value *= -1;
        }
      }
      constant = new ConstantDefinition(ValueFactory.create(value));
    } else if (constValue.FALSE() != null) {
      constant = new ConstantDefinition(ValueFactory.create(false));
    } else if (constValue.TRUE() != null) {
      constant = new ConstantDefinition(ValueFactory.create(true));
    } else if (constValue.UNDEFINED() != null) {
      constant = new ConstantDefinition(ValueFactory.create());
    } else {
      throw CompilerException.notSupportedException();
    }
    return constant;
  }

  private void processComplexIdentifier(BSLParser.ComplexIdentifierContext complexIdentifierContext) {
    if (complexIdentifierContext.globalMethodCall() != null) {
      processGlobalStatement(complexIdentifierContext.globalMethodCall());
      return;
    } else if (complexIdentifierContext.newExpression() != null) {
      processNewExpression(complexIdentifierContext.newExpression());
      return;
    }

    processIdentifier(complexIdentifierContext.IDENTIFIER().getText());

    // проверим, что идет дальше
    if (complexIdentifierContext.modifier() != null) {
      for (var modifier : complexIdentifierContext.modifier()) {

        if (modifier.accessCall() != null) {
          processAccessCall(modifier.accessCall(), true);
        } else if (modifier.accessProperty() != null) {
          throw CompilerException.notImplementedException("accessProperty");
        } else if (modifier.accessIndex() != null) {
          processExpression(modifier.accessIndex().expression(), new ArrayDeque<>());
          addCommand(OperationCode.PushIndexed, 0);
        } else {
          throw CompilerException.notSupportedException();
        }

      }
    }

  }

  private void processUnaryModifier(BSLParser.UnaryModifierContext child, Deque<ExpressionOperator> operators) {
    if (child.NOT_KEYWORD() != null) {
      operators.push(ExpressionOperator.NOT);
    } else if (child.PLUS() != null) {
      operators.push(ExpressionOperator.UNARY_PLUS);
    } else if (child.MINUS() != null) {
      operators.push(ExpressionOperator.UNARY_MINUS);
    } else {
      throw CompilerException.notNotSupportedExpressionOperator(child.getText());
    }
  }

  private void processGlobalStatement(BSLParser.GlobalMethodCallContext globalMethodCall) {
    var paramList = globalMethodCall.doCall().callParamList();
    processParamList(paramList);

    var identifier = globalMethodCall.methodName().getText();
    var optionalOperationCode = NativeGlobalMethod.getOperationCode(identifier);
    if (optionalOperationCode.isPresent()) {
      var opCode = optionalOperationCode.get();
      int arguments = NativeGlobalMethod.getArgumentsByOperationCode(opCode);

      int paramsCount = calcParams(paramList);
      if (paramsCount > arguments) {
        throw CompilerException.tooManyMethodArgumentsException();
      } else if (paramsCount < arguments) {
        throw CompilerException.tooFewMethodArgumentsException();
      }
      addCommand(opCode, arguments);

    } else {

      addCommand(OperationCode.ArgNum, calcParams(paramList));
      processMethodCall(globalMethodCall.methodName(), true);

    }
  }

  private void processNewExpression(BSLParser.NewExpressionContext newExpressionContext) {
    // TODO: хранить в отдельной стопке, не в контантах?
    var typeName = newExpressionContext.typeName().getText();
    var constant = new ConstantDefinition(ValueFactory.create(typeName));
    if (!imageCache.getConstants().contains(constant)) {
      imageCache.getConstants().add(constant);
    }
    addCommand(OperationCode.PushConst, imageCache.getConstants().indexOf(constant));

    // TODO:
    var argumentCount = 0;

    addCommand(OperationCode.NewInstance, argumentCount);
  }

  private void processParamList(BSLParser.CallParamListContext paramList) {
    paramList.callParam().forEach(callParamContext -> {
      if (callParamContext.expression() == null) {
        return;
      }
      processExpression(callParamContext.expression(), new ArrayDeque<>());
    });
  }

  private void processIdentifier(String identifier) {
    var address = compiler.findVariableInContext(identifier);
    if (address == null) {
      throw CompilerException.symbolNotFoundException(identifier);
    }

    if (address.getContextId() == compiler.getModuleContext().getMaxScopeIndex()) {
      addCommand(OperationCode.PushLoc, address.getSymbolId());
    } else {
      imageCache.getVariableRefs().add(address);
      addCommand(OperationCode.PushVar, imageCache.getVariableRefs().indexOf(address));
    }
  }

  private void processAccessCall(BSLParser.AccessCallContext accessCallContext, boolean ifFunction) {
    var paramList = accessCallContext.methodCall().doCall().callParamList();
    processParamList(paramList);
    addCommand(OperationCode.ArgNum, calcParams(paramList));

    var constant = new ConstantDefinition(ValueFactory.create(accessCallContext.methodCall().methodName().getText()));
    if (!imageCache.getConstants().contains(constant)) {
      imageCache.getConstants().add(constant);
    }
    var index = imageCache.getConstants().indexOf(constant);

    if (ifFunction) {
      addCommand(OperationCode.ResolveMethodFunc, index);
    } else {
      addCommand(OperationCode.ResolveMethodProc, index);
    }
  }

  private int calcParams(BSLParser.CallParamListContext callParamListContext) {
    var count = 0;
    for (var callParam : callParamListContext.callParam()) {
      if (callParam.getChildCount() > 0) {
        count++;
      }
    }
    return count;
  }

  private void processMethodCall(BSLParser.MethodNameContext methodNameContext, boolean isFunction) {
    var methodName = methodNameContext.getText();
    var address = compiler.findMethodInContext(methodName);
    if (address == null) {
      throw CompilerException.methodNotFound(methodName);
    }
    if (!imageCache.getMethodRefs().contains(address)) {
      imageCache.getMethodRefs().add(address);
    }
    var refs = imageCache.getMethodRefs().indexOf(address);
    if (isFunction) {
      addCommand(OperationCode.CallFunc, refs);
    } else {
      addCommand(OperationCode.CallProc, refs);
    }
  }

  private void correctBreakStatements(Compiler.NestedLoopInfo loop, int endLoop) {
    for (var breakCmdIndex : loop.breakStatements) {
      correctCommandArgument(breakCmdIndex, endLoop);
    }
  }

  private void exitTryBlocks() {
    assert nestedLoops.peek() != null;
    var tryBlocks = nestedLoops.peek().getTryNesting();
    if (tryBlocks > 0) {
      addCommand(OperationCode.ExitTry, tryBlocks);
    }
  }

  private void pushTryNesting() {
    if (!nestedLoops.isEmpty()) {
      nestedLoops.peek().tryNesting++;
    }
  }

  private void popTryNesting() {
    if (!nestedLoops.isEmpty()) {
      nestedLoops.peek().tryNesting--;
    }
  }

  private void addParametersToCurrentScope(ParameterInfo[] parameterInfos) {
    for (var parameterInfo : parameterInfos) {
      var variableInfo = new VariableInfo(parameterInfo.getName());
      var upperName = parameterInfo.getName().toUpperCase(Locale.ENGLISH);
      localScope.getVariables().add(variableInfo);
      localScope.getVariableNumbers().put(upperName, localScope.getVariables().indexOf(variableInfo));
    }
  }

  private void prepareModuleMethod(String methodName, boolean isBodyMethod) {
    currentMethodDescriptor = getMethodDescriptor(methodName);

    localScope = new SymbolScope();
    compiler.getModuleContext().pushScope(localScope);

    if (!isBodyMethod) {
      addParametersToCurrentScope(currentMethodDescriptor.getSignature().getParameters());
    }
  }

  private void pruneContextModuleMethod() {
    currentCommandReturnInMethod.clear();
    currentMethodDescriptor = null;
    compiler.getModuleContext().popScope(localScope);
    localScope = null;
  }

  private void processMethodEnd(boolean isBody) {
    if (isBody) {
      if (currentMethodDescriptor.getEntry() >= 0) {
        imageCache.setEntryPoint(imageCache.getMethods().size() - 1);
      }
    } else {
      var indexEndMethod = addReturn();
      currentCommandReturnInMethod.forEach(index -> correctCommandArgument(index, indexEndMethod));
      if (currentMethodDescriptor.getEntry() == DUMMY_ADDRESS) {
        currentMethodDescriptor.setEntry(indexEndMethod);
      }
    }

    currentMethodDescriptor.getVariables().addAll(localScope.getVariables());
  }

  private void addHiddenReturnForMethod() {
    // скрытый возврат Неопределено
    var constant = new ConstantDefinition(ValueFactory.create());
    imageCache.getConstants().add(constant);
    addCommand(OperationCode.PushConst, imageCache.getConstants().indexOf(constant));
  }

}
