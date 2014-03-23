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

abstract class AbstractValue(tp: Type?) : BasicValue(tp)

// we can easily change domain in a safe way!
open class IdRefValue(var domain: RefDomain, tp: Type?) : BasicValue(tp) {
    fun notNull(): IdRefValue {
        domain = RefDomain.NOTNULL
        return this
    }
}

class InstanceOfValue(val ref: IdRefValue): IdRefValue(RefDomain.NOTNULL, Type.INT_TYPE)

// may be it should be annotations aware interpreter
public open class IdRefBasicInterpreter(): Interpreter<IdRefValue>(Opcodes.ASM4) {
    // really it should be used ONLY by analyzer when creating a start frame
    // and by frame for constructing the dummy second value for two-slots values (long/double)
    public override fun newValue(`type`: Type?): IdRefValue? {
        if (`type` == null) {
            return IdRefValue(RefDomain.ANY, null)
        }
        when (`type`.getSort()) {
            Type.VOID ->
                return null
            Type.BOOLEAN,
            Type.CHAR,
            Type.BYTE,
            Type.SHORT,
            Type.INT ->
                return IdRefValue(RefDomain.NOTNULL, Type.INT_TYPE)
            Type.FLOAT ->
                return IdRefValue(RefDomain.NOTNULL, Type.FLOAT_TYPE)
            Type.LONG ->
                return IdRefValue(RefDomain.NOTNULL, Type.LONG_TYPE)
            Type.DOUBLE ->
                return IdRefValue(RefDomain.NOTNULL, Type.DOUBLE_TYPE)
            Type.ARRAY, Type.OBJECT ->
                return IdRefValue(RefDomain.NOTNULL, Type.getObjectType("java/lang/Object"))
            else ->
                throw Error("Internal error")
        }
    }

    public override fun newOperation(insn: AbstractInsnNode): IdRefValue {
        when (insn.getOpcode()) {
            Opcodes.ACONST_NULL ->
                return IdRefValue(RefDomain.ANY, Type.getObjectType("null"))
            Opcodes.ICONST_M1,
            Opcodes.ICONST_0,
            Opcodes.ICONST_1,
            Opcodes.ICONST_2,
            Opcodes.ICONST_3,
            Opcodes.ICONST_4,
            Opcodes.ICONST_5 ->
                return IdRefValue(RefDomain.NOTNULL, Type.INT_TYPE)
            Opcodes.LCONST_0,
            Opcodes.LCONST_1 ->
                return IdRefValue(RefDomain.NOTNULL, Type.LONG_TYPE)
            Opcodes.FCONST_0,
            Opcodes.FCONST_1,
            Opcodes.FCONST_2 ->
                return IdRefValue(RefDomain.NOTNULL, Type.FLOAT_TYPE)
            Opcodes.DCONST_0,
            Opcodes.DCONST_1 ->
                return IdRefValue(RefDomain.NOTNULL, Type.DOUBLE_TYPE)
            Opcodes.BIPUSH,
            Opcodes.SIPUSH ->
                return IdRefValue(RefDomain.NOTNULL, Type.INT_TYPE)
            Opcodes.LDC -> {
                val cst = ((insn as LdcInsnNode)).cst
                when (cst) {
                    is Int ->
                        return IdRefValue(RefDomain.NOTNULL, Type.INT_TYPE)
                    is Float ->
                        return IdRefValue(RefDomain.NOTNULL, Type.FLOAT_TYPE)
                    is Long ->
                        return IdRefValue(RefDomain.NOTNULL, Type.LONG_TYPE)
                    is Double ->
                        return IdRefValue(RefDomain.NOTNULL, Type.DOUBLE_TYPE)
                    is String ->
                        return IdRefValue(RefDomain.NOTNULL, Type.getObjectType("java/lang/String"))
                    is Type ->
                        when (cst.getSort()) {
                            Type.OBJECT,
                            Type.ARRAY ->
                                return IdRefValue(RefDomain.NOTNULL, Type.getObjectType("java/lang/Class"))
                            Type.METHOD ->
                                return IdRefValue(RefDomain.NOTNULL, Type.getObjectType("java/lang/invoke/MethodType"))
                            else ->
                                throw IllegalArgumentException("Illegal LDC constant " + cst)
                        }
                    is Handle ->
                        return IdRefValue(RefDomain.NOTNULL, Type.getObjectType("java/lang/invoke/MethodHandle"))
                    else ->
                        throw IllegalArgumentException("Illegal LDC constant " + cst)
                }
            }
            Opcodes.JSR ->
                return IdRefValue(RefDomain.ANY, Type.VOID_TYPE)
            // TODO - EXTENSION POINT
            Opcodes.GETSTATIC ->
                return IdRefValue(RefDomain.ANY, Type.getType(((insn as FieldInsnNode)).desc))
            Opcodes.NEW ->
                return IdRefValue(RefDomain.NOTNULL, Type.getObjectType(((insn as TypeInsnNode)).desc))
            else ->
                throw Error("Internal error.")
        }
    }

    public override fun copyOperation(insn: AbstractInsnNode?, value: IdRefValue?): IdRefValue? {
        return value
    }

    public override fun unaryOperation(insn: AbstractInsnNode, value: IdRefValue): IdRefValue? {
        when (insn.getOpcode()) {
            Opcodes.INEG,
            Opcodes.IINC,
            Opcodes.L2I,
            Opcodes.F2I,
            Opcodes.D2I,
            Opcodes.I2B,
            Opcodes.I2C,
            Opcodes.I2S ->
                return IdRefValue(RefDomain.NOTNULL, Type.INT_TYPE)
            Opcodes.FNEG,
            Opcodes.I2F,
            Opcodes.L2F,
            Opcodes.D2F ->
                return IdRefValue(RefDomain.NOTNULL, Type.FLOAT_TYPE)
            Opcodes.LNEG,
            Opcodes.I2L,
            Opcodes.F2L,
            Opcodes.D2L ->
                return IdRefValue(RefDomain.NOTNULL, Type.LONG_TYPE)
            Opcodes.DNEG,
            Opcodes.I2D,
            Opcodes.L2D,
            Opcodes.F2D ->
                return IdRefValue(RefDomain.NOTNULL, Type.DOUBLE_TYPE)
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
            // TODO - EXTENSION POINT
            Opcodes.GETFIELD ->
                return IdRefValue(RefDomain.ANY, Type.getType(((insn as FieldInsnNode)).desc))
            Opcodes.NEWARRAY ->
                when ((insn as IntInsnNode).operand) {
                    Opcodes.T_BOOLEAN ->
                        return IdRefValue(RefDomain.NOTNULL, Type.getType("[Z"))
                    Opcodes.T_CHAR ->
                        return IdRefValue(RefDomain.NOTNULL, Type.getType("[C"))
                    Opcodes.T_BYTE ->
                        return IdRefValue(RefDomain.NOTNULL, Type.getType("[B"))
                    Opcodes.T_SHORT ->
                        return IdRefValue(RefDomain.NOTNULL, Type.getType("[S"))
                    Opcodes.T_INT ->
                        return IdRefValue(RefDomain.NOTNULL, Type.getType("[I"))
                    Opcodes.T_FLOAT ->
                        return IdRefValue(RefDomain.NOTNULL, Type.getType("[F"))
                    Opcodes.T_DOUBLE ->
                        return IdRefValue(RefDomain.NOTNULL, Type.getType("[D"))
                    Opcodes.T_LONG ->
                        return IdRefValue(RefDomain.NOTNULL, Type.getType("[J"))
                    else -> {
                        throw AnalyzerException(insn, "Invalid array type")
                    }
                }
            Opcodes.ANEWARRAY -> {
                val desc = ((insn as TypeInsnNode)).desc
                return IdRefValue(RefDomain.NOTNULL, Type.getType("[" + Type.getObjectType(desc)))
            }
            // TODO - EXTENSION POINT
            Opcodes.ARRAYLENGTH ->
                return IdRefValue(RefDomain.NOTNULL, Type.INT_TYPE)
            Opcodes.ATHROW ->
                return null
            Opcodes.CHECKCAST ->
                return value
            // TODO - BOOLEAN OPERATION
            Opcodes.INSTANCEOF ->
                return InstanceOfValue(value)
            Opcodes.MONITORENTER,
            Opcodes.MONITOREXIT,
            Opcodes.IFNULL,
            Opcodes.IFNONNULL ->
                return null
            else ->
                throw Error("Internal error.")
        }
    }

    public override fun binaryOperation(insn: AbstractInsnNode, value1: IdRefValue, value2: IdRefValue): IdRefValue? {
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
                return IdRefValue(RefDomain.NOTNULL, Type.INT_TYPE)
            Opcodes.FALOAD,
            Opcodes.FADD,
            Opcodes.FSUB,
            Opcodes.FMUL,
            Opcodes.FDIV,
            Opcodes.FREM ->
                return IdRefValue(RefDomain.NOTNULL, Type.FLOAT_TYPE)
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
                return IdRefValue(RefDomain.NOTNULL, Type.LONG_TYPE)
            Opcodes.DALOAD,
            Opcodes.DADD,
            Opcodes.DSUB,
            Opcodes.DMUL,
            Opcodes.DDIV,
            Opcodes.DREM ->
                return IdRefValue(RefDomain.NOTNULL, Type.DOUBLE_TYPE)
            Opcodes.AALOAD ->
                return IdRefValue(RefDomain.ANY, Type.getObjectType("java/lang/Object"))
            Opcodes.LCMP,
            Opcodes.FCMPL,
            Opcodes.FCMPG,
            Opcodes.DCMPL,
            Opcodes.DCMPG ->
                return IdRefValue(RefDomain.NOTNULL, Type.INT_TYPE)
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

    public override fun ternaryOperation(insn: AbstractInsnNode, value1: IdRefValue, value2: IdRefValue, value3: IdRefValue): IdRefValue? {
        return null
    }

    public override fun naryOperation(insn: AbstractInsnNode, values: List<IdRefValue>): IdRefValue {
        when (insn.getOpcode()) {
            Opcodes.MULTIANEWARRAY ->
                return IdRefValue(RefDomain.NOTNULL, Type.getType(((insn as MultiANewArrayInsnNode)).desc))
            Opcodes.INVOKEDYNAMIC ->
                return IdRefValue(RefDomain.ANY, Type.getReturnType(((insn as InvokeDynamicInsnNode)).desc))
            else ->
                // TODO - EXTENSION POINT
                return IdRefValue(RefDomain.ANY, Type.getReturnType(((insn as MethodInsnNode)).desc))
        }
    }

    public override fun returnOperation(insn: AbstractInsnNode, value: IdRefValue?, expected: IdRefValue?) {
    }

    public override fun merge(v: IdRefValue?, w: IdRefValue?): IdRefValue? {
        throw UnsupportedOperationException("Not Implemented Yet")
    }
}
