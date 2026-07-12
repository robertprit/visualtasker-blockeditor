package de.visualtasker.blockeditor.ir

sealed interface IrExpression {
    data class ScreenContains(val text: String) : IrExpression
    data class And(val left: IrExpression, val right: IrExpression) : IrExpression
    data class Or(val left: IrExpression, val right: IrExpression) : IrExpression
    data class GetVariable(val name: String) : IrExpression
    data class LiteralBoolean(val value: Boolean) : IrExpression
    data class LiteralText(val value: String) : IrExpression
    data class Operate(
        val operator: String,
        val a: IrExpression,
        val b: IrExpression,
        val c: IrExpression? = null,
    ) : IrExpression
}

sealed interface IrStatement {
    data class ClickText(val text: String) : IrStatement
    data class Wait(val milliseconds: Long) : IrStatement
    data class Log(val message: String) : IrStatement
    data class SetVariable(val name: String, val value: String) : IrStatement
    data class Repeat(val times: Int, val body: List<IrStatement>) : IrStatement
    data class While(val condition: IrExpression, val body: List<IrStatement>) : IrStatement
    data class If(
        val condition: IrExpression,
        val thenBranch: List<IrStatement>,
        val elseIfBranches: List<ElseIfBranch> = emptyList(),
        val elseBranch: List<IrStatement> = emptyList(),
    ) : IrStatement
}

data class ElseIfBranch(
    val condition: IrExpression,
    val body: List<IrStatement>,
)

data class IrScript(
    val name: String,
    val statements: List<IrStatement>,
)
