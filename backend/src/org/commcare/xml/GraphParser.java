package org.commcare.xml;

import java.io.IOException;
import java.util.Hashtable;

import org.commcare.suite.model.Text;
import org.commcare.suite.model.graph.Annotation;
import org.commcare.suite.model.graph.BubbleSeries;
import org.commcare.suite.model.graph.Configurable;
import org.commcare.suite.model.graph.Graph;
import org.commcare.suite.model.graph.XYSeries;
import org.commcare.xml.util.InvalidStructureException;
import org.kxml2.io.KXmlParser;
import org.xmlpull.v1.XmlPullParserException;

/*
 * Parser for a <graph> element, typically used as a detail field's template.
 * @author jschweers
 */
public class GraphParser extends ElementParser<Graph> {
    public GraphParser(KXmlParser parser) {
        super(parser);
    }

    /*
     * (non-Javadoc)
     * @see org.commcare.xml.ElementParser#parse()
     */
    public Graph parse() throws InvalidStructureException, IOException, XmlPullParserException {
        Graph graph = new Graph();
        String type = parser.getAttributeValue(null, "type");
        if (type == null) {
            throw new InvalidStructureException("Expected attribute @type for element <" + parser.getName() + ">", parser);
        }
        graph.setType(type);

        int entryLevel = parser.getDepth();
        do {
            // <graph> contains an optional <configuration>, 0 to many <series>,
            // and 0 to many <annotation>, in any order.
            parser.nextTag();
            if (parser.getName().equals("configuration")) {
                // There's no reason for a graph to have multiple <configuration> elements,
                // but if it does, any later configuration settings will override earlier ones.
                parseConfiguration(graph);
            }
            if (parser.getName().equals("series")) {
                graph.addSeries(parseSeries(type));
            }
            if (parser.getName().equals("annotation")) {
                parseAnnotation(graph);
            }
        } while (parser.getDepth() > entryLevel);

        return graph;
    }

    /*
     * Helper for parse; handles a single annotation, which must contain an x
     * (which contains a single <text>), y (also contains a single <text>),
     * and then another <text> for the annotation's actual text.
     */
    private void parseAnnotation(Graph graph) throws InvalidStructureException, IOException, XmlPullParserException {
        checkNode("annotation");

        TextParser textParser = new TextParser(parser);

        nextStartTag();
        checkNode("x");
        nextStartTag();
        Text x = textParser.parse();

        nextStartTag();
        checkNode("y");
        nextStartTag();
        Text y = textParser.parse();

        nextStartTag();
        Text text = textParser.parse();

        parser.nextTag();

        graph.addAnnotation(new Annotation(x, y, text));
    }

    /*
     * Helper for parse; handles a configuration element, which is a set of <text> elements, each with an id.
     */
    private void parseConfiguration(Configurable data) throws InvalidStructureException, IOException, XmlPullParserException {
        checkNode("configuration");

        TextParser textParser = new TextParser(parser);
        do {
            parser.nextTag();
            if (parser.getName().equals("text")) {
                String id = parser.getAttributeValue(null, "id");
                Text t = textParser.parse();
                data.setConfiguration(id, t);
            }
        }
        while (parser.getEventType() != KXmlParser.END_TAG || !parser.getName().equals("configuration"));
    }

    /*
     * Helper for parse; handles a single series, which is an optional <configuration> followed by an <x>, a <y>,
     * and, if this graph is a bubble graph, a <radius>.
     */
    private XYSeries parseSeries(String type) throws InvalidStructureException, IOException, XmlPullParserException {
        checkNode("series");
        String nodeSet = parser.getAttributeValue(null, "nodeset");
        XYSeries series = type.equals(Graph.TYPE_BUBBLE) ? new BubbleSeries(nodeSet) : new XYSeries(nodeSet);

        nextStartTag();
        if (parser.getName().equals("configuration")) {
            parseConfiguration(series);
            nextStartTag();
        }

        checkNode("x");
        series.setX(parser.getAttributeValue(null, "function"));

        nextStartTag();
        checkNode("y");
        series.setY(parser.getAttributeValue(null, "function"));

        if (type.equals(Graph.TYPE_BUBBLE)) {
            nextStartTag();
            checkNode("radius");
            ((BubbleSeries)series).setRadius(parser.getAttributeValue(null, "function"));
        }

        while (parser.getEventType() != KXmlParser.END_TAG || !parser.getName().equals("series")) {
            parser.nextTag();
        }

        return series;
    }

    /*
     * Move parser along until it hits a start tag.
     */
    private void nextStartTag() throws IOException, XmlPullParserException {
        do {
            parser.nextTag();
        } while (parser.getEventType() != KXmlParser.START_TAG);
    }
}
