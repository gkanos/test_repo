package org.overture.codegen.vdm2java;

import java.util.LinkedList;
import java.util.List;

import org.overture.codegen.ir.INode;
import org.overture.codegen.ir.IRInfo;
import org.overture.codegen.ir.analysis.DepthFirstAnalysisAdaptor;
import org.overture.codegen.ir.expressions.AIntLiteralExpIR;
import org.overture.codegen.ir.types.AExternalTypeIR;
import org.overture.codegen.traces.TraceNames;
import org.overture.codegen.traces.TracesTrans;
import org.overture.codegen.trans.*;
import org.overture.codegen.trans.assistants.TransAssistantIR;
import org.overture.codegen.trans.conc.EvalPermPredTrans;
import org.overture.codegen.trans.conc.MainClassConcTrans;
import org.overture.codegen.trans.conc.MutexDeclTrans;
import org.overture.codegen.trans.conc.SentinelTrans;
import org.overture.codegen.trans.funcvalues.FuncValAssistant;
import org.overture.codegen.trans.funcvalues.FuncValPrefixes;
import org.overture.codegen.trans.funcvalues.FuncValTrans;
import org.overture.codegen.trans.iterator.ILanguageIterator;
import org.overture.codegen.trans.iterator.JavaLanguageIterator;
import org.overture.codegen.trans.letexps.FuncTrans;
import org.overture.codegen.trans.letexps.IfExpTrans;
import org.overture.codegen.trans.patterns.PatternTrans;
import org.overture.codegen.trans.patterns.PatternVarPrefixes;
import org.overture.codegen.trans.quantifier.CounterData;
import org.overture.codegen.trans.uniontypes.NonDetStmTrans;
import org.overture.codegen.trans.uniontypes.UnionTypeTrans;
import org.overture.codegen.trans.uniontypes.UnionTypeVarPrefixes;

public class JavaTransSeries
{
	private static final String OBJ_INIT_CALL_NAME_PREFIX = "cg_init_";

	private JavaCodeGen codeGen;
	private List<DepthFirstAnalysisAdaptor> series;
	private FuncValAssistant funcValAssist;

	public JavaTransSeries(JavaCodeGen codeGen)
	{
		this.codeGen = codeGen;
		this.series = new LinkedList<>();
		this.funcValAssist = new FuncValAssistant();
		setupAnalysis();
	}

	public FuncValAssistant getFuncValAssist()
	{
		return funcValAssist;
	}

	public List<DepthFirstAnalysisAdaptor> getSeries()
	{
		return series;
	}

	private List<DepthFirstAnalysisAdaptor> setupAnalysis()
	{
		// Data and functionality to support the transformations
		IRInfo info = codeGen.getIRGenerator().getIRInfo();
		JavaVarPrefixManager varMan = codeGen.getVarPrefixManager();
		IterationVarPrefixes iteVarPrefixes = varMan.getIteVarPrefixes();
		Exp2StmVarPrefixes exp2stmPrefixes = varMan.getExp2stmPrefixes();
		TraceNames tracePrefixes = varMan.getTracePrefixes();
		FuncValPrefixes funcValPrefixes = varMan.getFuncValPrefixes();
		PatternVarPrefixes patternPrefixes = varMan.getPatternPrefixes();
		UnionTypeVarPrefixes unionTypePrefixes = varMan.getUnionTypePrefixes();
		List<INode> cloneFreeNodes = codeGen.getJavaFormat().getValueSemantics().getCloneFreeNodes();

		TransAssistantIR transAssist = codeGen.getTransAssistant();
		IPostCheckCreator postCheckCreator = new JavaPostCheckCreator(varMan.postCheckMethodName());

		// Construct the transformations
		RenamedTrans renamedTr = new RenamedTrans(transAssist);
		ModuleRenamerTrans moduleRenamerTr = new ModuleRenamerTrans(transAssist);
		UnsupportedLibWarningTrans libWarnTr = new UnsupportedLibWarningTrans(transAssist);
		FieldOrderTrans fieldOrderTr = new FieldOrderTrans();
		AtomicStmTrans atomicTr = new AtomicStmTrans(transAssist, varMan.atomicTmpVar());
		NonDetStmTrans nonDetTr = new NonDetStmTrans(transAssist);
		FuncTrans funcTr = new FuncTrans(transAssist);
		DivideTrans divideTr = new DivideTrans(info);
		CallObjStmTrans callObjTr = new CallObjStmTrans(info);
		AssignStmTrans assignTr = new AssignStmTrans(transAssist);
		PrePostTrans prePostTr = new PrePostTrans(info);
		IfExpTrans ifExpTr = new IfExpTrans(transAssist);
		PolyFuncTrans polyTr = new PolyFuncTrans(transAssist);
		FuncValTrans funcValTr = new FuncValTrans(transAssist, funcValAssist, funcValPrefixes);
		ILanguageIterator langIte = new JavaLanguageIterator(transAssist, iteVarPrefixes);
		LetBeStTrans letBeStTr = new LetBeStTrans(transAssist, langIte, iteVarPrefixes);
		WhileStmTrans whileTr = new WhileStmTrans(transAssist, varMan.whileCond());
		Exp2StmTrans exp2stmTr = new Exp2StmTrans(iteVarPrefixes, transAssist, consCounterData(), langIte, exp2stmPrefixes);
		PatternTrans patternTr = new PatternTrans(iteVarPrefixes, transAssist, patternPrefixes, varMan.casesExp());
		PreCheckTrans preCheckTr = new PreCheckTrans(transAssist, new JavaValueSemanticsTag(false));
		PostCheckTrans postCheckTr = new PostCheckTrans(postCheckCreator, transAssist, varMan.funcRes(), new JavaValueSemanticsTag(false));
		IsExpSimplifyTrans isExpSimplifyTr = new IsExpSimplifyTrans(transAssist);
		IsExpTrans isExpTr = new IsExpTrans(transAssist, varMan.isExpSubject());
		SeqConvTrans seqConvTr = new SeqConvTrans(transAssist);
		TracesTrans tracesTr = new TracesTrans(transAssist, iteVarPrefixes, tracePrefixes, langIte, new JavaCallStmToStringBuilder(), cloneFreeNodes);
		UnionTypeTrans unionTypeTr = new UnionTypeTrans(transAssist, unionTypePrefixes, cloneFreeNodes);
		JavaToStringTrans javaToStringTr = new JavaToStringTrans(info);
		RecMethodsTrans recTr = new RecMethodsTrans(codeGen.getJavaFormat().getRecCreator());
		ConstructorTrans ctorTr = new ConstructorTrans(transAssist, OBJ_INIT_CALL_NAME_PREFIX);
		ImportsTrans impTr = new ImportsTrans(info);
		SlAccessTrans slAccessTr = new SlAccessTrans();
		JUnit4Trans junitTr = new JUnit4Trans(transAssist, codeGen);

		// Start concurrency transformations
		SentinelTrans sentinelTr = new SentinelTrans(info, varMan.getConcPrefixes());
		MainClassConcTrans mainClassTr = new MainClassConcTrans(info, varMan.getConcPrefixes());
		MutexDeclTrans mutexTr = new MutexDeclTrans(info, varMan.getConcPrefixes());
		EvalPermPredTrans evalPermPredTr = new EvalPermPredTrans(transAssist, varMan.getConcPrefixes());
		// End concurrency transformations

		// Set up order of transformations
		series.add(renamedTr);
		series.add(moduleRenamerTr);
		series.add(libWarnTr);
		series.add(fieldOrderTr);
		series.add(atomicTr);
		series.add(nonDetTr);
		series.add(divideTr);
		series.add(assignTr);
		series.add(callObjTr);
		series.add(polyTr);
		series.add(funcTr);
		series.add(prePostTr);
		series.add(ifExpTr);
		series.add(funcValTr);
		series.add(letBeStTr);
		series.add(whileTr);
		series.add(exp2stmTr);
		series.add(tracesTr);
		series.add(patternTr);
		series.add(preCheckTr);
		series.add(postCheckTr);
		series.add(isExpSimplifyTr);
		series.add(isExpTr);
		series.add(unionTypeTr);
		series.add(javaToStringTr);
		series.add(sentinelTr);
		series.add(mutexTr);
		series.add(mainClassTr);
		series.add(seqConvTr);
		series.add(evalPermPredTr);
		series.add(recTr);
		series.add(ctorTr);
		series.add(impTr);
		series.add(slAccessTr);
		series.add(junitTr);

		return series;
	}

	private CounterData consCounterData()
	{
		AExternalTypeIR type = new AExternalTypeIR();
		type.setName("Long");

		IRInfo irInfo = codeGen.getIRGenerator().getIRInfo();
		AIntLiteralExpIR initExp = irInfo.getExpAssistant().consIntLiteral(0);

		return new CounterData(type, initExp);
	}

	public void clear()
	{
		funcValAssist.getFuncValInterfaces().clear();
	}
}
