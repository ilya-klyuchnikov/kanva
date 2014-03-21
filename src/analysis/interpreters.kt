package kanva.analysis

import org.objectweb.asm.tree.analysis.BasicValue
import org.objectweb.asm.tree.analysis.Interpreter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.Handle
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.TypeInsnNode
import org.objectweb.asm.tree.IntInsnNode
import org.objectweb.asm.tree.analysis.AnalyzerException
import org.objectweb.asm.tree.MultiANewArrayInsnNode
import org.objectweb.asm.tree.InvokeDynamicInsnNode
import org.objectweb.asm.tree.MethodInsnNode

// may be it should be annotations aware interpreter
public open class IdRefBasicInterpreter(): Interpreter<BasicValue>(Opcodes.ASM4) {

    override fun newValue(`type`: Type?): BasicValue? {
        if (`type` == null) {
            return BasicValue.UNINITIALIZED_VALUE
        }
        when (`type`.getSort()) {
            Type.VOID ->
                return null
            Type.BOOLEAN,
            Type.CHAR,
            Type.BYTE,
            Type.SHORT,
            Type.INT ->
                return BasicValue.INT_VALUE
            Type.FLOAT ->
                return BasicValue.FLOAT_VALUE
            Type.LONG ->
                return BasicValue.LONG_VALUE
            Type.DOUBLE ->
                return BasicValue.DOUBLE_VALUE
            Type.ARRAY, Type.OBJECT ->
                return BasicValue.REFERENCE_VALUE
            else ->
                throw Error("Internal error")
        }
    }

    override fun newOperation(insn: AbstractInsnNode): BasicValue? {
        when (insn.getOpcode()) {
            Opcodes.ACONST_NULL ->
                return newValue(Type.getObjectType("null"))
            Opcodes.ICONST_M1,
            Opcodes.ICONST_0,
            Opcodes.ICONST_1,
            Opcodes.ICONST_2,
            Opcodes.ICONST_3,
            Opcodes.ICONST_4,
            Opcodes.ICONST_5 ->
                return BasicValue.INT_VALUE
            Opcodes.LCONST_0,
            Opcodes.LCONST_1 ->
                return BasicValue.LONG_VALUE
            Opcodes.FCONST_0,
            Opcodes.FCONST_1,
            Opcodes.FCONST_2 ->
                return BasicValue.FLOAT_VALUE
            Opcodes.DCONST_0,
            Opcodes.DCONST_1 ->
                return BasicValue.DOUBLE_VALUE
            Opcodes.BIPUSH, Opcodes.SIPUSH ->
                return BasicValue.INT_VALUE
            Opcodes.LDC -> {
                val cst = ((insn as LdcInsnNode)).cst
                when (cst) {
                    is Int ->
                        return BasicValue.INT_VALUE
                    is Float ->
                        return BasicValue.FLOAT_VALUE
                    is Long ->
                        return BasicValue.LONG_VALUE
                    is Double ->
                        return BasicValue.DOUBLE_VALUE
                    is String ->
                        return newValue(Type.getObjectType("java/lang/String"))
                    is Type ->
                        when (cst.getSort()) {
                            Type.OBJECT, Type.ARRAY ->
                                return newValue(Type.getObjectType("java/lang/Class"))
                            Type.METHOD ->
                                return newValue(Type.getObjectType("java/lang/invoke/MethodType"))
                            else ->
                                throw IllegalArgumentException("Illegal LDC constant " + cst)
                        }
                    is Handle ->
                        return newValue(Type.getObjectType("java/lang/invoke/MethodHandle"))
                    else ->
                        throw IllegalArgumentException("Illegal LDC constant " + cst)
                }
            }
            Opcodes.JSR ->
                return BasicValue.RETURNADDRESS_VALUE
            Opcodes.GETSTATIC ->
                return newValue(Type.getType(((insn as FieldInsnNode)).desc))
            Opcodes.NEW ->
                return newValue(Type.getObjectType(((insn as TypeInsnNode)).desc))
            else ->
                throw Error("Internal error.")
        }
    }

    override fun copyOperation(insn: AbstractInsnNode?, value: BasicValue?): BasicValue? {
        return value
    }

    override fun unaryOperation(insn: AbstractInsnNode, value: BasicValue): BasicValue? {
        when (insn.getOpcode()) {
            Opcodes.INEG,
            Opcodes.IINC,
            Opcodes.L2I,
            Opcodes.F2I,
            Opcodes.D2I,
            Opcodes.I2B,
            Opcodes.I2C,
            Opcodes.I2S ->
                return BasicValue.INT_VALUE
            Opcodes.FNEG,
            Opcodes.I2F,
            Opcodes.L2F,
            Opcodes.D2F ->
                return BasicValue.FLOAT_VALUE
            Opcodes.LNEG,
            Opcodes.I2L,
            Opcodes.F2L,
            Opcodes.D2L ->
                return BasicValue.LONG_VALUE
            Opcodes.DNEG,
            Opcodes.I2D,
            Opcodes.L2D,
            Opcodes.F2D ->
                return BasicValue.DOUBLE_VALUE
            Opcodes.IFEQ,
            Opcodes.IFNE,
            Opcodes.IFLT,
            Opcodes.IFGE,
            Opcodes.IFGT,
            Opcodes.IFLE,
            Opcodes.TABLESWITCH,
            Opcodes.LOOKUPSWITCH,
            Opcodes.IRETURN,
            Opcodes.LRETURN,
            Opcodes.FRETURN,
            Opcodes.DRETURN,
            Opcodes.ARETURN,
            Opcodes.PUTSTATIC ->
                return null
            Opcodes.GETFIELD ->
                return newValue(Type.getType(((insn as FieldInsnNode)).desc))
            Opcodes.NEWARRAY ->
                when ((insn as IntInsnNode).operand) {
                    Opcodes.T_BOOLEAN ->
                        return newValue(Type.getType("[Z"))
                    Opcodes.T_CHAR ->
                        return newValue(Type.getType("[C"))
                    Opcodes.T_BYTE ->
                        return newValue(Type.getType("[B"))
                    Opcodes.T_SHORT ->
                        return newValue(Type.getType("[S"))
                    Opcodes.T_INT ->
                        return newValue(Type.getType("[I"))
                    Opcodes.T_FLOAT ->
                        return newValue(Type.getType("[F"))
                    Opcodes.T_DOUBLE ->
                        return newValue(Type.getType("[D"))
                    Opcodes.T_LONG ->
                        return newValue(Type.getType("[J"))
                    else -> {
                        throw AnalyzerException(insn, "Invalid array type")
                    }
                }
            Opcodes.ANEWARRAY -> {
                val desc = ((insn as TypeInsnNode)).desc
                return newValue(Type.getType("[" + Type.getObjectType(desc)))
            }
            Opcodes.ARRAYLENGTH ->
                return BasicValue.INT_VALUE
            Opcodes.ATHROW ->
                return null
            Opcodes.CHECKCAST -> {
                val desc = ((insn as TypeInsnNode)).desc
                return newValue(Type.getObjectType(desc))
            }
            // TODO
            Opcodes.INSTANCEOF ->
                return BasicValue.INT_VALUE
            Opcodes.MONITORENTER,
            Opcodes.MONITOREXIT,
            Opcodes.IFNULL,
            Opcodes.IFNONNULL ->
                return null
            else ->
                throw Error("Internal error.")
        }
    }

    override fun binaryOperation(insn: AbstractInsnNode, value1: BasicValue, value2: BasicValue): BasicValue? {
        when (insn.getOpcode()) {
            Opcodes.IALOAD,
            Opcodes.BALOAD,
            Opcodes.CALOAD,
            Opcodes.SALOAD,
            Opcodes.IADD,
            Opcodes.ISUB,
            Opcodes.IMUL,
            Opcodes.IDIV,
            Opcodes.IREM,
            Opcodes.ISHL,
            Opcodes.ISHR,
            Opcodes.IUSHR,
            Opcodes.IAND,
            Opcodes.IOR,
            Opcodes.IXOR ->
                return BasicValue.INT_VALUE
            Opcodes.FALOAD,
            Opcodes.FADD,
            Opcodes.FSUB,
            Opcodes.FMUL,
            Opcodes.FDIV,
            Opcodes.FREM ->
                return BasicValue.FLOAT_VALUE
            Opcodes.LALOAD,
            Opcodes.LADD,
            Opcodes.LSUB,
            Opcodes.LMUL,
            Opcodes.LDIV,
            Opcodes.LREM,
            Opcodes.LSHL,
            Opcodes.LSHR,
            Opcodes.LUSHR,
            Opcodes.LAND,
            Opcodes.LOR,
            Opcodes.LXOR ->
                return BasicValue.LONG_VALUE
            Opcodes.DALOAD,
            Opcodes.DADD,
            Opcodes.DSUB,
            Opcodes.DMUL,
            Opcodes.DDIV,
            Opcodes.DREM ->
                return BasicValue.DOUBLE_VALUE
            Opcodes.AALOAD ->
                return BasicValue.REFERENCE_VALUE
            Opcodes.LCMP,
            Opcodes.FCMPL,
            Opcodes.FCMPG,
            Opcodes.DCMPL,
            Opcodes.DCMPG ->
                return BasicValue.INT_VALUE
            Opcodes.IF_ICMPEQ,
            Opcodes.IF_ICMPNE,
            Opcodes.IF_ICMPLT,
            Opcodes.IF_ICMPGE,
            Opcodes.IF_ICMPGT,
            Opcodes.IF_ICMPLE,
            Opcodes.IF_ACMPEQ,
            Opcodes.IF_ACMPNE,
            Opcodes.PUTFIELD ->
                return null
            else ->
                throw Error("Internal error.")
        }
    }

    override fun ternaryOperation(insn: AbstractInsnNode, value1: BasicValue, value2: BasicValue, value3: BasicValue): BasicValue? {
        return null
    }

    override fun naryOperation(insn: AbstractInsnNode, values: List<BasicValue>): BasicValue? {
        val opcode = insn.getOpcode()
        if (opcode == Opcodes.MULTIANEWARRAY) {
            return newValue(Type.getType(((insn as MultiANewArrayInsnNode)).desc))
        } else
            if (opcode == Opcodes.INVOKEDYNAMIC) {
                return newValue(Type.getReturnType(((insn as InvokeDynamicInsnNode)).desc))
            } else {
                return newValue(Type.getReturnType(((insn as MethodInsnNode)).desc))
            }
    }

    override fun returnOperation(insn: AbstractInsnNode, value: BasicValue?, expected: BasicValue?) {
    }

    override fun merge(v: BasicValue?, w: BasicValue?): BasicValue? {
        if (!v.equals(w)) {
            return BasicValue.UNINITIALIZED_VALUE
        }
        return v
    }
}
