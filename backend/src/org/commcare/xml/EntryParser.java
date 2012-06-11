/**
 * 
 */
package org.commcare.xml;

import java.io.IOException;
import java.util.Hashtable;
import java.util.Vector;

import org.commcare.suite.model.Entry;
import org.commcare.suite.model.SessionDatum;
import org.commcare.suite.model.Text;
import org.commcare.xml.util.InvalidStructureException;
import org.javarosa.core.model.instance.DataInstance;
import org.javarosa.core.model.instance.ExternalDataInstance;
import org.kxml2.io.KXmlParser;
import org.xmlpull.v1.XmlPullParserException;

/**
 * @author ctsims
 *
 */
public class EntryParser extends ElementParser<Entry> {

	public EntryParser(KXmlParser parser) {
		super(parser);
	}

	/* (non-Javadoc)
	 * @see org.commcare.xml.ElementParser#parse()
	 */
	public Entry parse() throws InvalidStructureException, IOException, XmlPullParserException {
		this.checkNode("entry");
		
		String xFormNamespace = "";
		Vector<SessionDatum> data = new Vector<SessionDatum>();
		Hashtable<String, DataInstance> instances = new Hashtable<String, DataInstance>();

		
		String commandId = "";
		Text commandText = null;
		String imageURI = null;
		String audioURI = null;
		Object[] displayArr;  //Should *ALWAYS* be [Text commandText, String imageURI, String audioURI]
			
		while(nextTagInBlock("entry")) {
			if(parser.getName().equals("form")) {
				xFormNamespace = parser.nextText();
//				imageURI = parser.getAttributeValue
			}
			else if(parser.getName().equals("command")) {
				commandId = parser.getAttributeValue(null, "id");
				parser.nextTag();
				if(parser.getName().equals("text")){
					commandText = new TextParser(parser).parse();
				}else if(parser.getName().equals("display")){
					displayArr = parseDisplayBlock();
					//check that we have a commandText;
					if(displayArr[0] == null) throw new InvalidStructureException("Expected CommandText in Display block",parser);
					else commandText = (Text)displayArr[0];
					
					imageURI = (String)displayArr[1];
					audioURI = (String)displayArr[2];
				}
			}
			else if("instance".equals(parser.getName().toLowerCase())) {
				String instanceId = parser.getAttributeValue(null, "id");
				String location = parser.getAttributeValue(null,"src");
				instances.put(instanceId, new ExternalDataInstance(location, instanceId));
				continue;
			}
			else if(parser.getName().equals("session")) {
				while(nextTagInBlock("session")) {
					SessionDatumParser parser = new SessionDatumParser(this.parser);
					data.addElement(parser.parse());
				}
			} 
			else if(parser.getName().equals("entity") || parser.getName().equals("details")) {
				throw new InvalidStructureException("Incompatible CaseXML 1.0 elements detected in <entry>. " + 
						                             parser.getName() + " is not a valid construct in 2.0 CaseXML", parser);
			}
		}
		Entry e = new Entry(commandId, commandText, data, xFormNamespace, imageURI, audioURI, instances);
		return e;
	}
}
