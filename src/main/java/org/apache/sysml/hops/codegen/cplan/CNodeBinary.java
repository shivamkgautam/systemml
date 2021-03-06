/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.sysml.hops.codegen.cplan;

import org.apache.commons.lang.StringUtils;
import org.apache.sysml.hops.codegen.template.TemplateUtils;
import org.apache.sysml.parser.Expression.DataType;
import org.apache.sysml.runtime.util.UtilFunctions;


public class CNodeBinary extends CNode
{
	public enum BinType {
		//matrix multiplication operations
		DOT_PRODUCT, VECT_MATRIXMULT, VECT_OUTERMULT_ADD,
		//vector-scalar-add operations
		VECT_MULT_ADD, VECT_DIV_ADD, VECT_MINUS_ADD, VECT_PLUS_ADD,
		VECT_POW_ADD, VECT_MIN_ADD, VECT_MAX_ADD,
		VECT_EQUAL_ADD, VECT_NOTEQUAL_ADD, VECT_LESS_ADD, 
		VECT_LESSEQUAL_ADD, VECT_GREATER_ADD, VECT_GREATEREQUAL_ADD,
		VECT_CBIND_ADD, VECT_XOR_ADD,
		//vector-scalar operations
		VECT_MULT_SCALAR, VECT_DIV_SCALAR, VECT_MINUS_SCALAR, VECT_PLUS_SCALAR,
		VECT_POW_SCALAR, VECT_MIN_SCALAR, VECT_MAX_SCALAR,
		VECT_EQUAL_SCALAR, VECT_NOTEQUAL_SCALAR, VECT_LESS_SCALAR, 
		VECT_LESSEQUAL_SCALAR, VECT_GREATER_SCALAR, VECT_GREATEREQUAL_SCALAR,
		VECT_CBIND,
		VECT_XOR_SCALAR, VECT_BITWAND_SCALAR,
		//vector-vector operations
		VECT_MULT, VECT_DIV, VECT_MINUS, VECT_PLUS, VECT_MIN, VECT_MAX, VECT_EQUAL, 
		VECT_NOTEQUAL, VECT_LESS, VECT_LESSEQUAL, VECT_GREATER, VECT_GREATEREQUAL,
		VECT_XOR, VECT_BITWAND,
		//scalar-scalar operations
		MULT, DIV, PLUS, MINUS, MODULUS, INTDIV, 
		LESS, LESSEQUAL, GREATER, GREATEREQUAL, EQUAL,NOTEQUAL,
		MIN, MAX, AND, OR, XOR, LOG, LOG_NZ, POW,
		BITWAND,
		MINUS1_MULT, MINUS_NZ;

		public static boolean contains(String value) {
			for( BinType bt : values()  )
				if( bt.name().equals(value) )
					return true;
			return false;
		}
		
		public boolean isCommutative() {
			boolean ssComm = (this==EQUAL || this==NOTEQUAL 
				|| this==PLUS || this==MULT || this==MIN || this==MAX
				|| this==OR || this==AND || this==XOR || this==BITWAND);
			boolean vsComm = (this==VECT_EQUAL_SCALAR || this==VECT_NOTEQUAL_SCALAR 
					|| this==VECT_PLUS_SCALAR || this==VECT_MULT_SCALAR 
					|| this==VECT_MIN_SCALAR || this==VECT_MAX_SCALAR
					|| this==VECT_XOR_SCALAR || this==VECT_BITWAND_SCALAR );
			boolean vvComm = (this==VECT_EQUAL || this==VECT_NOTEQUAL 
					|| this==VECT_PLUS || this==VECT_MULT || this==VECT_MIN || this==VECT_MAX
					|| this==VECT_XOR || this==BinType.VECT_BITWAND);
			return ssComm || vsComm || vvComm;
		}
		
		public String getTemplate(boolean sparseLhs, boolean sparseRhs, boolean scalarVector, boolean scalarInput) {
			switch (this) {
				case DOT_PRODUCT:
					return sparseLhs ? "    double %TMP% = LibSpoofPrimitives.dotProduct(%IN1v%, %IN2%, %IN1i%, %POS1%, %POS2%, alen);\n" :
									"    double %TMP% = LibSpoofPrimitives.dotProduct(%IN1%, %IN2%, %POS1%, %POS2%, %LEN%);\n";
				case VECT_MATRIXMULT:
					return sparseLhs ? "    double[] %TMP% = LibSpoofPrimitives.vectMatrixMult(%IN1v%, %IN2%, %IN1i%, %POS1%, %POS2%, alen, len);\n" :
									"    double[] %TMP% = LibSpoofPrimitives.vectMatrixMult(%IN1%, %IN2%, %POS1%, %POS2%, %LEN%);\n";
				case VECT_OUTERMULT_ADD:
					return  sparseLhs ? "    LibSpoofPrimitives.vectOuterMultAdd(%IN1v%, %IN2%, %OUT%, %IN1i%, %POS1%, %POS2%, %POSOUT%, alen, %LEN1%, %LEN2%);\n" :
							sparseRhs ? "    LibSpoofPrimitives.vectOuterMultAdd(%IN1%, %IN2v%, %OUT%, %POS1%, %IN2i%, %POS2%, %POSOUT%, alen, %LEN1%, %LEN2%);\n" :
									"    LibSpoofPrimitives.vectOuterMultAdd(%IN1%, %IN2%, %OUT%, %POS1%, %POS2%, %POSOUT%, %LEN1%, %LEN2%);\n";
				
				//vector-scalar-add operations
				case VECT_MULT_ADD:
				case VECT_DIV_ADD:
				case VECT_MINUS_ADD:
				case VECT_PLUS_ADD:
				case VECT_POW_ADD:
				case VECT_XOR_ADD:
				case VECT_MIN_ADD:
				case VECT_MAX_ADD:	
				case VECT_EQUAL_ADD:
				case VECT_NOTEQUAL_ADD:
				case VECT_LESS_ADD:
				case VECT_LESSEQUAL_ADD:
				case VECT_GREATER_ADD:
				case VECT_GREATEREQUAL_ADD:
				case VECT_CBIND_ADD: {
					String vectName = getVectorPrimitiveName();
					if( scalarVector )
						return sparseLhs ? "    LibSpoofPrimitives.vect"+vectName+"Add(%IN1%, %IN2v%, %OUT%, %IN2i%, %POS2%, %POSOUT%, alen, %LEN%);\n" : 
										"    LibSpoofPrimitives.vect"+vectName+"Add(%IN1%, %IN2%, %OUT%, %POS2%, %POSOUT%, %LEN%);\n";
					else	
						return sparseLhs ? "    LibSpoofPrimitives.vect"+vectName+"Add(%IN1v%, %IN2%, %OUT%, %IN1i%, %POS1%, %POSOUT%, alen, %LEN%);\n" : 
										"    LibSpoofPrimitives.vect"+vectName+"Add(%IN1%, %IN2%, %OUT%, %POS1%, %POSOUT%, %LEN%);\n";
				}
				
				//vector-scalar operations
				case VECT_MULT_SCALAR:
				case VECT_DIV_SCALAR:
				case VECT_MINUS_SCALAR:
				case VECT_PLUS_SCALAR:
				case VECT_POW_SCALAR:
				case VECT_XOR_SCALAR:
				case VECT_BITWAND_SCALAR:
				case VECT_MIN_SCALAR:
				case VECT_MAX_SCALAR:
				case VECT_EQUAL_SCALAR:
				case VECT_NOTEQUAL_SCALAR:
				case VECT_LESS_SCALAR:
				case VECT_LESSEQUAL_SCALAR:
				case VECT_GREATER_SCALAR:
				case VECT_GREATEREQUAL_SCALAR: {
					String vectName = getVectorPrimitiveName();
					if( scalarVector )
						return sparseRhs ? "    double[] %TMP% = LibSpoofPrimitives.vect"+vectName+"Write(%IN1%, %IN2v%, %IN2i%, %POS2%, alen, %LEN%);\n" : 
										"    double[] %TMP% = LibSpoofPrimitives.vect"+vectName+"Write(%IN1%, %IN2%, %POS2%, %LEN%);\n";
					else	
						return sparseLhs ? "    double[] %TMP% = LibSpoofPrimitives.vect"+vectName+"Write(%IN1v%, %IN2%, %IN1i%, %POS1%, alen, %LEN%);\n" : 
										"    double[] %TMP% = LibSpoofPrimitives.vect"+vectName+"Write(%IN1%, %IN2%, %POS1%, %LEN%);\n";
				}
				
				case VECT_CBIND:
					if( scalarInput )
						return  "    double[] %TMP% = LibSpoofPrimitives.vectCbindWrite(%IN1%, %IN2%);\n";
					else
						return sparseLhs ? 
								"    double[] %TMP% = LibSpoofPrimitives.vectCbindWrite(%IN1v%, %IN2%, %IN1i%, %POS1%, alen, %LEN%);\n" : 
								"    double[] %TMP% = LibSpoofPrimitives.vectCbindWrite(%IN1%, %IN2%, %POS1%, %LEN%);\n";
				
				//vector-vector operations
				case VECT_MULT:
				case VECT_DIV:
				case VECT_MINUS:
				case VECT_PLUS:
				case VECT_XOR:
				case VECT_BITWAND:
				case VECT_MIN:
				case VECT_MAX:
				case VECT_EQUAL:
				case VECT_NOTEQUAL:
				case VECT_LESS:
				case VECT_LESSEQUAL:
				case VECT_GREATER:
				case VECT_GREATEREQUAL: {
					String vectName = getVectorPrimitiveName();
					return sparseLhs ? 
						"    double[] %TMP% = LibSpoofPrimitives.vect"+vectName+"Write(%IN1v%, %IN2%, %IN1i%, %POS1%, %POS2%, alen, %LEN%);\n" : 
						   sparseRhs ?
						"    double[] %TMP% = LibSpoofPrimitives.vect"+vectName+"Write(%IN1%, %IN2v%, %POS1%, %IN2i%, %POS2%, alen, %LEN%);\n" : 
						"    double[] %TMP% = LibSpoofPrimitives.vect"+vectName+"Write(%IN1%, %IN2%, %POS1%, %POS2%, %LEN%);\n";
				}
				
				//scalar-scalar operations
				case MULT:
					return "    double %TMP% = %IN1% * %IN2%;\n";
				
				case DIV:
					return "    double %TMP% = %IN1% / %IN2%;\n";
				case PLUS:
					return "    double %TMP% = %IN1% + %IN2%;\n";
				case MINUS:
					return "    double %TMP% = %IN1% - %IN2%;\n";
				case MODULUS:
					return "    double %TMP% = LibSpoofPrimitives.mod(%IN1%, %IN2%);\n";
				case INTDIV: 
					return "    double %TMP% = LibSpoofPrimitives.intDiv(%IN1%, %IN2%);\n";
				case LESS:
					return "    double %TMP% = (%IN1% < %IN2%) ? 1 : 0;\n";
				case LESSEQUAL:
					return "    double %TMP% = (%IN1% <= %IN2%) ? 1 : 0;\n";
				case GREATER:
					return "    double %TMP% = (%IN1% > %IN2%) ? 1 : 0;\n";
				case GREATEREQUAL: 
					return "    double %TMP% = (%IN1% >= %IN2%) ? 1 : 0;\n";
				case EQUAL:
					return "    double %TMP% = (%IN1% == %IN2%) ? 1 : 0;\n";
				case NOTEQUAL: 
					return "    double %TMP% = (%IN1% != %IN2%) ? 1 : 0;\n";
				
				case MIN:
					return "    double %TMP% = (%IN1% <= %IN2%) ? %IN1% : %IN2%;\n";
				case MAX:
					return "    double %TMP% = (%IN1% >= %IN2%) ? %IN1% : %IN2%;\n";
				case LOG:
					return "    double %TMP% = Math.log(%IN1%)/Math.log(%IN2%);\n";
				case LOG_NZ:
					return "    double %TMP% = (%IN1% == 0) ? 0 : Math.log(%IN1%)/Math.log(%IN2%);\n";	
				case POW:
					return "    double %TMP% = Math.pow(%IN1%, %IN2%);\n";
				case MINUS1_MULT:
					return "    double %TMP% = 1 - %IN1% * %IN2%;\n";
				case MINUS_NZ:
					return "    double %TMP% = (%IN1% != 0) ? %IN1% - %IN2% : 0;\n";
				case XOR:
					return "    double %TMP% = ( (%IN1% != 0) != (%IN2% != 0) ) ? 1 : 0;\n";
				case BITWAND:
					return "    double %TMP% = LibSpoofPrimitives.bwAnd(%IN1%, %IN2%);\n";

				default: 
					throw new RuntimeException("Invalid binary type: "+this.toString());
			}
		}
		public boolean isVectorPrimitive() {
			return isVectorScalarPrimitive() 
				|| isVectorVectorPrimitive()
				|| isVectorMatrixPrimitive();
		}
		public boolean isVectorScalarPrimitive() {
			return this == VECT_DIV_SCALAR || this == VECT_MULT_SCALAR 
				|| this == VECT_MINUS_SCALAR || this == VECT_PLUS_SCALAR
				|| this == VECT_POW_SCALAR 
				|| this == VECT_MIN_SCALAR || this == VECT_MAX_SCALAR
				|| this == VECT_EQUAL_SCALAR || this == VECT_NOTEQUAL_SCALAR
				|| this == VECT_LESS_SCALAR || this == VECT_LESSEQUAL_SCALAR
				|| this == VECT_GREATER_SCALAR || this == VECT_GREATEREQUAL_SCALAR
				|| this == VECT_CBIND
				|| this == VECT_XOR_SCALAR || this == VECT_BITWAND_SCALAR;
		}
		public boolean isVectorVectorPrimitive() {
			return this == VECT_DIV || this == VECT_MULT 
				|| this == VECT_MINUS || this == VECT_PLUS
				|| this == VECT_MIN || this == VECT_MAX
				|| this == VECT_EQUAL || this == VECT_NOTEQUAL
				|| this == VECT_LESS || this == VECT_LESSEQUAL
				|| this == VECT_GREATER || this == VECT_GREATEREQUAL
				|| this == VECT_XOR || this == VECT_BITWAND;
		}
		public boolean isVectorMatrixPrimitive() {
			return this == VECT_MATRIXMULT
				|| this == VECT_OUTERMULT_ADD;
		}
		public BinType getVectorAddPrimitive() {
			return BinType.valueOf("VECT_"+getVectorPrimitiveName().toUpperCase()+"_ADD");
		}
		public String getVectorPrimitiveName() {
			String [] tmp = this.name().split("_");
			return StringUtils.capitalize(tmp[1].toLowerCase());
		}
	}
	
	private final BinType _type;
	
	public CNodeBinary( CNode in1, CNode in2, BinType type ) {
		//canonicalize commutative matrix-scalar operations
		//to increase reuse potential
		if( type.isCommutative() && in1 instanceof CNodeData 
			&& in1.getDataType()==DataType.SCALAR ) {
			CNode tmp = in1;
			in1 = in2; 
			in2 = tmp;
		}
		
		_inputs.add(in1);
		_inputs.add(in2);
		_type = type;
		setOutputDims();
	}

	public BinType getType() {
		return _type;
	}
	
	@Override
	public String codegen(boolean sparse) {
		if( isGenerated() )
			return "";
		
		StringBuilder sb = new StringBuilder();
		
		//generate children
		sb.append(_inputs.get(0).codegen(sparse));
		sb.append(_inputs.get(1).codegen(sparse));
		
		//generate binary operation (use sparse template, if data input)
		boolean lsparseLhs = sparse && _inputs.get(0) instanceof CNodeData 
			&& _inputs.get(0).getVarname().startsWith("a");
		boolean lsparseRhs = sparse && _inputs.get(1) instanceof CNodeData 
			&& _inputs.get(1).getVarname().startsWith("a");
		boolean scalarInput = _inputs.get(0).getDataType().isScalar();
		boolean scalarVector = (_inputs.get(0).getDataType().isScalar()
			&& _inputs.get(1).getDataType().isMatrix());
		String var = createVarname();
		String tmp = _type.getTemplate(lsparseLhs, lsparseRhs, scalarVector, scalarInput);
		tmp = tmp.replace("%TMP%", var);
		
		//replace input references and start indexes
		for( int j=0; j<2; j++ ) {
			String varj = _inputs.get(j).getVarname();
			
			//replace sparse and dense inputs
			tmp = tmp.replace("%IN"+(j+1)+"v%", varj+"vals");
			tmp = tmp.replace("%IN"+(j+1)+"i%", varj+"ix");
			tmp = tmp.replace("%IN"+(j+1)+"%", 
				varj.startsWith("b") ? varj + ".values(rix)" : varj );
			
			//replace start position of main input
			tmp = tmp.replace("%POS"+(j+1)+"%", (_inputs.get(j) instanceof CNodeData 
				&& _inputs.get(j).getDataType().isMatrix()) ? (!varj.startsWith("b")) ? varj+"i" : 
				(TemplateUtils.isMatrix(_inputs.get(j)) && _type!=BinType.VECT_MATRIXMULT) ? 
				varj + ".pos(rix)" : "0" : "0");
		}
		//replace length information (e.g., after matrix mult)
		if( _type == BinType.VECT_OUTERMULT_ADD ) {
			for( int j=0; j<2; j++ )
				tmp = tmp.replace("%LEN"+(j+1)+"%", _inputs.get(j).getVectorLength());
		}
		else { //general case 
			CNode mInput = getIntermediateInputVector();
			if( mInput != null )
				tmp = tmp.replace("%LEN%", mInput.getVectorLength());
		}
		
		sb.append(tmp);
		
		//mark as generated
		_generated = true;
		
		return sb.toString();
	}
	
	private CNode getIntermediateInputVector() {
		for( int i=0; i<2; i++ )
			if( getInput().get(i).getDataType().isMatrix() )
				return getInput().get(i);
		return null;
	} 
	
	@Override
	public String toString() {
		switch(_type) {
			case DOT_PRODUCT:              return "b(dot)";
			case VECT_MATRIXMULT:          return "b(vmm)";
			case VECT_OUTERMULT_ADD:       return "b(voma)";
			case VECT_MULT_ADD:            return "b(vma)";
			case VECT_DIV_ADD:             return "b(vda)";
			case VECT_MINUS_ADD:           return "b(vmia)";
			case VECT_PLUS_ADD:            return "b(vpa)";
			case VECT_POW_ADD:             return "b(vpowa)";
			case VECT_MIN_ADD:             return "b(vmina)";
			case VECT_MAX_ADD:             return "b(vmaxa)";
			case VECT_EQUAL_ADD:           return "b(veqa)";
			case VECT_NOTEQUAL_ADD:        return "b(vneqa)";
			case VECT_LESS_ADD:            return "b(vlta)";
			case VECT_LESSEQUAL_ADD:       return "b(vltea)";
			case VECT_GREATEREQUAL_ADD:    return "b(vgtea)";
			case VECT_GREATER_ADD:         return "b(vgta)";
			case VECT_CBIND_ADD:           return "b(vcbinda)";
			case VECT_MULT_SCALAR:         return "b(vm)";
			case VECT_DIV_SCALAR:          return "b(vd)";
			case VECT_MINUS_SCALAR:        return "b(vmi)";
			case VECT_PLUS_SCALAR:         return "b(vp)";
			case VECT_XOR_SCALAR:          return "v(vxor)";
			case VECT_POW_SCALAR:          return "b(vpow)";
			case VECT_MIN_SCALAR:          return "b(vmin)";
			case VECT_MAX_SCALAR:          return "b(vmax)";
			case VECT_EQUAL_SCALAR:        return "b(veq)";
			case VECT_NOTEQUAL_SCALAR:     return "b(vneq)";
			case VECT_LESS_SCALAR:         return "b(vlt)";
			case VECT_LESSEQUAL_SCALAR:    return "b(vlte)";
			case VECT_GREATEREQUAL_SCALAR: return "b(vgte)";
			case VECT_GREATER_SCALAR:      return "b(vgt)";
			case VECT_MULT:                return "b(v2m)";
			case VECT_DIV:                 return "b(v2d)";
			case VECT_MINUS:               return "b(v2mi)";
			case VECT_PLUS:                return "b(v2p)";
			case VECT_XOR:                 return "b(v2xor)";
			case VECT_MIN:                 return "b(v2min)";
			case VECT_MAX:                 return "b(v2max)";
			case VECT_EQUAL:               return "b(v2eq)";
			case VECT_NOTEQUAL:            return "b(v2neq)";
			case VECT_LESS:                return "b(v2lt)";
			case VECT_LESSEQUAL:           return "b(v2lte)";
			case VECT_GREATEREQUAL:        return "b(v2gte)";
			case VECT_GREATER:             return "b(v2gt)";
			case VECT_CBIND:               return "b(cbind)";
			case MULT:                     return "b(*)";
			case DIV:                      return "b(/)";
			case PLUS:                     return "b(+)";
			case MINUS:                    return "b(-)";
			case POW:                      return "b(^)";
			case MODULUS:                  return "b(%%)";
			case INTDIV:                   return "b(%/%)";
			case LESS:                     return "b(<)";
			case LESSEQUAL:                return "b(<=)";
			case GREATER:                  return "b(>)";
			case GREATEREQUAL:             return "b(>=)";
			case EQUAL:                    return "b(==)";
			case NOTEQUAL:                 return "b(!=)";
			case OR:                       return "b(|)";
			case AND:                      return "b(&)";
			case XOR:                      return "b(xor)";
			case BITWAND:                  return "b(bitwAnd)";
			case MINUS1_MULT:              return "b(1-*)";
			case MINUS_NZ:                 return "b(-nz)";
			default: return "b("+_type.name().toLowerCase()+")";
		}
	}
	
	@Override
	public void setOutputDims()
	{
		switch(_type) {
			//VECT
			case VECT_MULT_ADD: 
			case VECT_DIV_ADD:
			case VECT_MINUS_ADD:
			case VECT_PLUS_ADD:
			case VECT_POW_ADD:
			case VECT_MIN_ADD:
			case VECT_MAX_ADD:
			case VECT_EQUAL_ADD: 
			case VECT_NOTEQUAL_ADD: 
			case VECT_LESS_ADD: 
			case VECT_LESSEQUAL_ADD: 
			case VECT_GREATER_ADD: 
			case VECT_GREATEREQUAL_ADD:
			case VECT_CBIND_ADD:
			case VECT_XOR_ADD:
				boolean vectorScalar = _inputs.get(1).getDataType()==DataType.SCALAR;
				_rows = _inputs.get(vectorScalar ? 0 : 1)._rows;
				_cols = _inputs.get(vectorScalar ? 0 : 1)._cols;
				_dataType = DataType.MATRIX;
				break;
			
			case VECT_CBIND:
				_rows = _inputs.get(0)._rows;
				_cols = _inputs.get(0)._cols+1;
				_dataType = DataType.MATRIX;
				break;
			
			case VECT_OUTERMULT_ADD:
				_rows = _inputs.get(0)._cols;
				_cols = _inputs.get(1)._cols;
				_dataType = DataType.MATRIX;
				break;
				
			case VECT_DIV_SCALAR: 	
			case VECT_MULT_SCALAR:
			case VECT_MINUS_SCALAR:
			case VECT_PLUS_SCALAR:
			case VECT_XOR_SCALAR:
			case VECT_BITWAND_SCALAR:
			case VECT_POW_SCALAR:
			case VECT_MIN_SCALAR:
			case VECT_MAX_SCALAR:
			case VECT_EQUAL_SCALAR: 
			case VECT_NOTEQUAL_SCALAR: 
			case VECT_LESS_SCALAR: 
			case VECT_LESSEQUAL_SCALAR: 
			case VECT_GREATER_SCALAR: 
			case VECT_GREATEREQUAL_SCALAR:
			
			case VECT_DIV: 	
			case VECT_MULT:
			case VECT_MINUS:
			case VECT_PLUS:
			case VECT_XOR:
			case VECT_BITWAND:
			case VECT_MIN:
			case VECT_MAX:
			case VECT_EQUAL: 
			case VECT_NOTEQUAL: 
			case VECT_LESS: 
			case VECT_LESSEQUAL: 
			case VECT_GREATER: 
			case VECT_GREATEREQUAL:	
				boolean scalarVector = (_inputs.get(0).getDataType()==DataType.SCALAR);
				_rows = _inputs.get(scalarVector ? 1 : 0)._rows;
				_cols = _inputs.get(scalarVector ? 1 : 0)._cols;
				_dataType= DataType.MATRIX;
				break;
			
			case VECT_MATRIXMULT:
				_rows = _inputs.get(0)._rows;
				_cols = _inputs.get(1)._cols;
				_dataType = DataType.MATRIX;
				break;
				
			case DOT_PRODUCT: 
			
			//SCALAR Arithmetic
			case MULT: 
			case DIV: 
			case PLUS: 
			case MINUS: 
			case MINUS1_MULT:
			case MINUS_NZ:
			case MODULUS: 
			case INTDIV: 	
			//SCALAR Comparison
			case LESS: 
			case LESSEQUAL: 
			case GREATER: 
			case GREATEREQUAL: 
			case EQUAL: 
			case NOTEQUAL: 
			//SCALAR LOGIC
			case MIN: 
			case MAX: 
			case AND: 
			case OR:
			case XOR:
			case BITWAND:
			case LOG: 
			case LOG_NZ:
			case POW: 
				_rows = 0;
				_cols = 0;
				_dataType= DataType.SCALAR;
				break;	
		}
	}
	
	@Override
	public int hashCode() {
		if( _hash == 0 ) {
			_hash = UtilFunctions.intHashCode(
				super.hashCode(), _type.hashCode());
		}
		return _hash;
	}
	
	@Override 
	public boolean equals(Object o) {
		if( !(o instanceof CNodeBinary) )
			return false;
		
		CNodeBinary that = (CNodeBinary) o;
		return super.equals(that)
			&& _type == that._type;
	}
}
