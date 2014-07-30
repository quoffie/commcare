package org.commcare.suite.model.graph;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

import org.commcare.suite.model.DetailTemplate;
import org.commcare.suite.model.Text;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.model.instance.AbstractTreeElement;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.core.util.externalizable.DeserializationException;
import org.javarosa.core.util.externalizable.ExtUtil;
import org.javarosa.core.util.externalizable.ExtWrapList;
import org.javarosa.core.util.externalizable.ExtWrapMap;
import org.javarosa.core.util.externalizable.Externalizable;
import org.javarosa.core.util.externalizable.PrototypeFactory;
import org.javarosa.xpath.XPathParseTool;
import org.javarosa.xpath.expr.XPathExpression;
import org.javarosa.xpath.parser.XPathSyntaxException;

public class Graph implements Externalizable, DetailTemplate, Configurable {
	public static final String TYPE_XY = "xy";
	public static final String TYPE_BUBBLE = "bubble";

	private String type;
	private Vector<XYSeries> series;
	private Hashtable<String, Text> configuration;
	private Vector<Annotation> annotations;
	
	public Graph() {
		series = new Vector<XYSeries>();
		configuration = new Hashtable<String, Text>();
		annotations = new Vector<Annotation>();
	}
	
	public String getType() {
		return type;
	}
	
	public void setType(String type) {
		this.type = type;
	}
	
	public void addSeries(XYSeries s) {
		series.addElement(s);
	}
	
	public void addAnnotation(Annotation a) {
		annotations.addElement(a);
	}
	
	public Text getConfiguration(String key) {
		return configuration.get(key);
	}
	
	public void setConfiguration(String key, Text value) {
		configuration.put(key, value);
	}

	public Enumeration getConfigurationKeys() {
		return configuration.keys();
	}
	
	public void readExternal(DataInputStream in, PrototypeFactory pf) throws IOException, DeserializationException {
		// TODO Auto-generated method stub
		ExtUtil.readString(in);
		configuration = (Hashtable<String, Text>)ExtUtil.read(in, new ExtWrapMap(String.class, Text.class), pf);
		series = (Vector<XYSeries>)ExtUtil.read(in, new ExtWrapList(XYSeries.class), pf);
		annotations = (Vector<Annotation>)ExtUtil.read(in,  new ExtWrapList(Annotation.class), pf);
	}

	public void writeExternal(DataOutputStream out) throws IOException {
		ExtUtil.writeString(out, type);
		ExtUtil.write(out, new ExtWrapMap(configuration));
		ExtUtil.write(out, new ExtWrapList(series));
		ExtUtil.write(out, new ExtWrapList(annotations));
	}

	public GraphData evaluate(EvaluationContext context) {
		GraphData data = new GraphData();
		data.setType(type);
		evaluateSeries(data, context);
		evaluateAnnotations(data, context);
		evaluateConfiguration(this, data, context);
		return data;
	}
	
	private void evaluateAnnotations(GraphData graphData, EvaluationContext context) {
		for (Annotation a : annotations) {
			graphData.addAnnotation(new AnnotationData(
				Double.valueOf(a.getX().evaluate(context)), 
				Double.valueOf(a.getY().evaluate(context)), 
				a.getAnnotation().evaluate(context)
			));
		}
	}
	
	private void evaluateConfiguration(Configurable template, ConfigurableData data, EvaluationContext context) {
		Enumeration e = template.getConfigurationKeys();
		while (e.hasMoreElements()) {
			String key = (String) e.nextElement();
			data.setConfiguration(key, template.getConfiguration(key).evaluate(context));
		}
	}
	
	private void evaluateSeries(GraphData graphData, EvaluationContext context) {
		try {
			for (XYSeries s : series) {
				Vector<TreeReference> refList = context.expandReference(s.getNodeSet());
				SeriesData seriesData = new SeriesData();
				evaluateConfiguration(s, seriesData, context);
				for (TreeReference ref : refList) {
					EvaluationContext refContext = new EvaluationContext(context, ref);
					Double x = s.evaluateX(refContext);
					Double y = s.evaluateY(refContext);
					if (x != null && y != null) {
						if (graphData.getType().equals(Graph.TYPE_BUBBLE)) {
							Double radius = ((BubbleSeries) s).evaluateRadius(refContext);
							seriesData.addPoint(new BubblePointData(x, y, radius));
						}
						else {
							seriesData.addPoint(new XYPointData(x, y));
						}
					}
				}
				graphData.addSeries(seriesData);
			}
		}
		catch (XPathSyntaxException e) {
			e.printStackTrace();
		}
	}

}
