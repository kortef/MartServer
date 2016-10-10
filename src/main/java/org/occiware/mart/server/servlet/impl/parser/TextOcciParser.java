/*
 * Copyright 2016 cgourdin.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 * Contributors:
 * - Christophe Gourdin <christophe.gourdin@inria.fr>
 */
package org.occiware.mart.server.servlet.impl.parser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EDataType;
import org.eclipse.emf.ecore.EEnum;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.impl.EEnumImpl;
import org.occiware.clouddesigner.occi.Action;
import org.occiware.clouddesigner.occi.Attribute;
import org.occiware.clouddesigner.occi.AttributeState;
import org.occiware.clouddesigner.occi.Entity;
import org.occiware.clouddesigner.occi.Kind;
import org.occiware.clouddesigner.occi.Link;
import org.occiware.clouddesigner.occi.Mixin;
import org.occiware.clouddesigner.occi.util.Occi2Ecore;
import org.occiware.mart.server.servlet.exception.AttributeParseException;
import org.occiware.mart.server.servlet.exception.CategoryParseException;
import org.occiware.mart.server.servlet.exception.ResponseParseException;
import org.occiware.mart.server.servlet.facade.AbstractRequestParser;
import org.occiware.mart.server.servlet.model.ConfigurationManager;
import org.occiware.mart.server.servlet.utils.Constants;
import org.occiware.mart.server.servlet.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * text/occi rendering are in headers not in body, this is not a text/plain (or text/occi+plain).
 * @author Christophe Gourdin
 */
public class TextOcciParser extends AbstractRequestParser {

    private static final Logger LOGGER = LoggerFactory.getLogger(TextOcciParser.class);
    
    @Override
    public void parseOcciCategories(HttpHeaders headers, HttpServletRequest request) throws CategoryParseException {
        MultivaluedMap<String, String> map = headers.getRequestHeaders();

        List<String> values;
        List<String> mixinsToAdd = new ArrayList<>();

        for (Map.Entry<String, List<String>> entry : map.entrySet()) {
            String key = entry.getKey();
            values = entry.getValue();
            if (key.equals(Constants.CATEGORY)) {
                // Check the class value.
                // check if this is a kind or a mixin. 
                // As it may have kind and mixins, the value return will be a kind class before.
                // Example of value: 
                // compute; scheme="http://schemas.ogf.org/occi/infrastructure#"; class="kind";

                for (String value : values) {
                    String line = Constants.CATEGORY + ": " + value;
                    Matcher matcher = Constants.PATTERN_CATEGORY.matcher(line);
                    if (!matcher.find()) {
                        continue;
                    }
                    String term = matcher.group(Constants.GROUP_TERM);
                    String scheme = matcher.group(Constants.GROUP_SCHEME);
                    String categoryClass = matcher.group(Constants.GROUP_CLASS);
                    if (categoryClass.equalsIgnoreCase(Constants.CLASS_KIND)) {
                        // Assign the kind.
                        setKind(scheme + term);
                        continue;
                    }
                    if (categoryClass.equalsIgnoreCase(Constants.CLASS_MIXIN)) {
                        mixinsToAdd.add(scheme + term);
                        continue;
                    }
                    if (categoryClass.equalsIgnoreCase(Constants.CLASS_ACTION)) {
                        setAction(scheme + term);
                    }
                }
            }
        }
        if (!mixinsToAdd.isEmpty()) {
            setMixins(mixinsToAdd);
        }

    }

    /**
     * Convert X-OCCI-Attribute to map key --> value.
     *
     * @param headers
     * @param request
     */
    @Override
    public void parseOcciAttributes(HttpHeaders headers, HttpServletRequest request) throws AttributeParseException {
        Map<String, String> attrs = new HashMap<>();
        MultivaluedMap<String, String> map = headers.getRequestHeaders();
        List<String> values;
        for (Map.Entry<String, List<String>> entry : map.entrySet()) {
            String key = entry.getKey();
            values = entry.getValue();
            if (key.equalsIgnoreCase(Constants.X_OCCI_ATTRIBUTE)) {
                for (String value : values) {
                    // Parse the value:
                    String[] attr = value.split("=");
                    if (attr != null && attr.length > 0) {
                        attr[0] = attr[0].replace("\"", "");
                        attr[1] = attr[1].replace("\"", "");
                        attrs.put(attr[0], attr[1]);
                    }

                }
            }
        }
        setOcciAttributes(attrs);
    }
    
    /**
     * Parse a text/occi Response.
     * @param object
     * @param status
     * @return
     * @throws ResponseParseException 
     */
    @Override
    public Response parseResponse(Object object, Response.Status status) throws ResponseParseException {
        Response response = null;
        // Case 1 : Object is a Response object.
        if (object instanceof Response) {
            if (status != null && status.equals(Response.Status.OK)) {
                response = Response.fromResponse((Response)object)
                    .header("Server", Constants.OCCI_SERVER_HEADER)
                    .type(Constants.MEDIA_TYPE_TEXT_OCCI)
                    .entity("OK")
                    .status(status)
                    .build();
            } else {
                response = Response.fromResponse((Response)object)
                    .header("Server", Constants.OCCI_SERVER_HEADER)
                    .type(Constants.MEDIA_TYPE_TEXT_OCCI)
                    .status(status)
                    .build();
            }
        } 
        // Case 2 : Object is a String.
        if (object instanceof String) {
            if (status != null && status.equals(Response.Status.OK)) {
                response = Response.status(status)
                    .header("Server", Constants.OCCI_SERVER_HEADER)
                    .header("content", (String)object)
                    .entity("OK")
                    .type(Constants.MEDIA_TYPE_TEXT_OCCI)
                    .build();
            } else {
                response = Response.status(status)
                    .header("Server", Constants.OCCI_SERVER_HEADER)
                    .header("content", (String)object)
                    .entity((String)object)
                    .type(Constants.MEDIA_TYPE_TEXT_OCCI)
                    .build();
            }
            
        }
        if (object instanceof Entity) {
            // Build an object response from entity occiware object model.
            Entity entity = (Entity)object;
            response = renderEntityResponse(entity, status);
            
        }
        
        if (response == null) {
            throw new ResponseParseException("Cannot parse the object to text/occi representation.");
        }
        
        return response;
        
    }

    @Override
    public String getEntityUUID() {
        return super.getEntityUUID();
    }

    /**
     * Build interface /-/ for accept type : text/occi.
     *
     * @param categoryFilter
     * @param user
     * @return interface to set in header.
     */
    @Override
    public Response getInterface(String categoryFilter, final String user) {
        // Define kindsConf and mixinsConf from configuration used extension kinds and mixins object.
        super.getInterface(categoryFilter, user);
        Response response;
        List<Kind> kinds = getKindsConf();

        List<Mixin> mixins = getMixinsConf();

        StringBuilder sb = renderOcciKindsActions(kinds, true);

        sb.append(renderOcciMixins(mixins, true));

        String msg = sb.toString();
        if (msg != null && !msg.isEmpty()) {
            response = Response.ok().entity("ok")
                .header("Server", Constants.OCCI_SERVER_HEADER)
                .header("", sb.toString())
                .build();
        } else {
            // May not be called.
            response = Response.noContent().build();
        }
        
        return response;
    }

    /**
     * Get text/occi for occi Kinds and actions.
     *
     * @param kinds
     * @param detailed
     * @return
     */
    private StringBuilder renderOcciKindsActions(List<Kind> kinds, boolean detailed) {
        StringBuilder sb = new StringBuilder();

        for (Kind kind : kinds) {
            sb.append(kind.getTerm())
                    .append(";scheme=\"").append(kind.getScheme()).append("\";class=\"kind\"");
            if (detailed) {
                sb.append(";title=\"").append(kind.getTitle()).append('\"');
                Kind parent = kind.getParent();
                if (parent != null) {
                    sb.append(";rel=\"").append(parent.getScheme()).append(parent.getTerm()).append('\"');
                }
                sb.append(";location=\"").append(ConfigurationManager.getLocation(kind)).append('\"');
                appendAttributes(sb, kind.getAttributes());
                appendActions(sb, kind.getActions());
            }
        }
        return sb;
    }

    /**
     * Get text/occi for occi mixins and dependencies.
     * @param mixins
     * @param detailed
     * @return 
     */
    private StringBuilder renderOcciMixins(List<Mixin> mixins, boolean detailed) {
        StringBuilder sb = new StringBuilder();
        for (Mixin mixin : mixins) {
            sb.append(mixin.getTerm())
                    .append(";scheme=\"").append(mixin.getScheme()).append("\";class=\"mixin\"");
            if (detailed) {
                sb.append(";title=\"").append(mixin.getTitle()).append('\"');
                List<Mixin> mixinsDep = mixin.getDepends();
                if (!mixinsDep.isEmpty()) {
                    sb.append(";rel=\"");
                    String sep = "";
                    for (Mixin md : mixinsDep) {
                        sb.append(sep).append(md.getScheme()).append(md.getTerm());
                        sep = " ";
                    }
                    sb.append('\"');
                }
                sb.append(";location=\"").append(ConfigurationManager.getLocation(mixin)).append('\"');
                appendAttributes(sb, mixin.getAttributes());
                appendActions(sb, mixin.getActions());
            }

        }
        return sb;
    }
    /**
     * Append attributes in text/occi format for kinds, mixins and entities definition.
     * @param sb
     * @param attributes 
     */
    private void appendAttributes(StringBuilder sb, List<Attribute> attributes) {
		if(!attributes.isEmpty()) {
			sb.append(";attributes=\"");
			String sep = "";
			for(Attribute attribute : attributes) {
				sb.append(sep).append(attribute.getName());
				if(attribute.isRequired() || !attribute.isMutable()) {
					sb.append('{');
					if(!attribute.isMutable()) {
						sb.append("immutable");
						if(attribute.isRequired()) {
							sb.append(' ');
						}
					}
					if(attribute.isRequired()) {
						sb.append("required");
					}
					sb.append('}');
				}
				sep = " ";
			}
			sb.append('\"');
		}
	}

    private String asString(Action action) {
        StringBuilder sb = new StringBuilder();
        sb.append(action.getTerm())
                .append(";scheme=\"").append(action.getScheme()).append("\";class=\"action\"")
                .append(";title=\"").append(action.getTitle()).append('\"');
        appendAttributes(sb, action.getAttributes());
        return sb.toString();
    }
    
    /**
     * Append action to string builder.
     * @param sb
     * @param actions 
     */
    private void appendActions(StringBuilder sb, List<Action> actions) {
		if(!actions.isEmpty()) {
			sb.append(";actions=\"");
			String sep = "";
			for(Action action : actions) {
				sb.append(sep).append(action.getScheme()).append(action.getTerm());
				sep = " ";
			}
			sb.append('\"');
		}
	}
    
    /**
     * Get the kind on header, for text/occi.
     *
     * @param headers
     * @return
     */
    public String getKindFromHeader(HttpHeaders headers) {
        String kind = null;

        List<String> kindsVal = Utils.getFromValueFromHeaders(headers, Constants.CATEGORY);
        // Search for Class="kind" value.
        String[] vals;
        boolean kindVal;
        for (String line : kindsVal) {
            kindVal = false;
            vals = line.split(";");
            // Check class="kind".
            for (String val : vals) {
                if (val.contains("class=\"" + Constants.CLASS_KIND + "\"")) {
                    kindVal = true;
                }
            }
            if (kindVal) {
                // Get the kind value.
                for (String val : vals) {
                    if (val.contains(Constants.CATEGORY)) {
                        String category = val.trim();

                        // Get the value.
                        kind = category.split(":")[1];
                        LOGGER.info("Kind value is : " + kind);
                    }
                }
            }
        }
        return kind;
    }

    /**
     * Default with response status ok.
     * @param object
     * @return
     * @throws ResponseParseException 
     */
    @Override
    public Response parseResponse(Object object) throws ResponseParseException {
        return parseResponse(object, Response.Status.OK);
    }

    /**
     * Render a response with entity object input.
     * @param entity
     * @param status
     * @return a Response object conform to text/occi specification.
     */
    private Response renderEntityResponse(Entity entity, Response.Status status) {
        
        Response response;
        
        String categories = renderCategory(entity.getKind(), false);
        
        // if entity as mixins, update categories as expected.
        List<Mixin> mixinsTmp = entity.getMixins();
        for (Mixin mixin : mixinsTmp) {
            categories += renderCategory(mixin, false);
        }
        
        // Link header.
        String relativeLocation = ConfigurationManager.getLocation(entity);
        String absoluteEntityLocation = getServerURI().toString() + relativeLocation;
        
        // Convert all actions to links.
        javax.ws.rs.core.Link[] links = renderActionsLink(entity, absoluteEntityLocation);
        
        response = Response.status(status)
                .header("Server", Constants.OCCI_SERVER_HEADER)
                .header(Constants.CATEGORY, categories)
                .header(Constants.X_OCCI_ATTRIBUTE, renderAttributes(entity))
                .header(Constants.X_OCCI_LOCATION, renderXOCCILocationAttr(entity))
                .entity("OK")
                .links(links)
                .build();
        
        
//        PrintWriter out = response.getWriter();
//		response.setContentType(TEXT_PLAIN);
//		out.println(CATEGORY + ": " + asCategory(entity.getKind(), false));
//		for(Mixin mixin : entity.getMixins()) {
//			out.println(CATEGORY + ": " + asCategory(mixin, false));
//		}
//		out.println(X_OCCI_ATTRIBUTE + ": " + OCCI_CORE_ID + "=\"" + entity.getId() + '\"');
//		if(entity instanceof Link) {
//			Link link = (Link)entity;
//			out.println(X_OCCI_ATTRIBUTE + ": " + OCCI_CORE_SOURCE + "=\"" + getLocation(link.getSource()) + '\"');
//			out.println(X_OCCI_ATTRIBUTE + ": " + OCCI_CORE_TARGET + "=\"" + getLocation(link.getTarget()) + '\"');
//		}		
//		for(AttributeState attribute : entity.getAttributes()) {
//			String value = attribute.getValue();
//			// TODO: add " " only for string and enum attributes.
//			value = "\"" + value + "\"";
//			out.println(X_OCCI_ATTRIBUTE + ": " + attribute.getName() + '=' + value);
//		}
        return response;
    }
    
    
    /**
     * Render a category with its definitions attributes etc.
     * @param kind
     * @param detailed
     * @return 
     */
    private String renderCategory(Kind kind, boolean detailed) {
		StringBuilder sb = new StringBuilder();
		sb.append(kind.getTerm())
		  .append(";scheme=\"").append(kind.getScheme()).append("\";class=\"kind\"");
		if(detailed) {
			sb.append(";title=\"").append(kind.getTitle()).append('\"');
			Kind parent = kind.getParent();
			if(parent != null) {
				sb.append(";rel=\"").append(parent.getScheme()).append(parent.getTerm()).append('\"');			
			}
			sb.append(";location=\"").append(ConfigurationManager.getLocation(kind)).append('\"');
			appendAttributes(sb, kind.getAttributes());
			appendActions(sb, kind.getActions());
		}
		return sb.toString();
	}

    /**
     * As kind we render here a mixin.
     * @param mixin
     * @param detailed
     * @return 
     */
	private String renderCategory(Mixin mixin, boolean detailed) {
		StringBuilder sb = new StringBuilder();
		sb.append(mixin.getTerm())
		  .append(";scheme=\"").append(mixin.getScheme()).append("\";class=\"mixin\"");
		if(detailed) {
			sb.append(";title=\"").append(mixin.getTitle()).append('\"');
			List<Mixin> mixinsTmp = mixin.getDepends();
			if(!mixinsTmp.isEmpty()) {
				sb.append(";rel=\"");
				String sep = "";
				for(Mixin md : mixinsTmp) {
					sb.append(sep).append(md.getScheme()).append(md.getTerm());
					sep = " ";
				}
				sb.append('\"');
			}
			sb.append(";location=\"").append(ConfigurationManager.getLocation(mixin)).append('\"');
			appendAttributes(sb, mixin.getAttributes());
			appendActions(sb, mixin.getActions());
		}
		return sb.toString();
	}

	
    /**
     * Render Link: header (cf spec text rendering).
     * @param entity
     * @param entityAbsolutePath 
     * @return An array of Link to set to header.
     */
    private javax.ws.rs.core.Link[] renderActionsLink(final Entity entity, final String entityAbsolutePath) {
        LOGGER.info("Entity location : " + entityAbsolutePath);
        javax.ws.rs.core.Link linkAbsoluteEntityPath = javax.ws.rs.core.Link.fromUri(entityAbsolutePath)
                .title(entity.getKind().getTerm())
                .build();
        javax.ws.rs.core.Link[] links;
        int linkSize = 1;
        int current = 0;
        
        // For each actions we add the link like this : <mylocation?action=actionTerm>; \
        //    rel="http://actionScheme#actionTerm"
        List<Action> actionsTmp = entity.getKind().getActions();
        linkSize += actionsTmp.size();
        links = new javax.ws.rs.core.Link[linkSize];
        links[0] = linkAbsoluteEntityPath;
        current++;
        javax.ws.rs.core.Link actionLink;
        
        // We render the Link header.
        if (!actionsTmp.isEmpty()) {
            // We render the different link here.
            for (Action action : actionsTmp) {
                
                actionLink = javax.ws.rs.core.Link.fromUri(entityAbsolutePath)
                        .title(action.getTerm())
                        .rel(action.getScheme() + action.getTerm())
                        .build();
                links[current] = actionLink;
                current++;
//                
//                tmp = "<" + location + "?action=" + action.getTerm() + ">; \\ ";
//                rel = "\"" + action.getScheme() + action.getTerm() + "\";";
//                sb.append(tmp);
//                sb.append(rel);
            }
        }
//        location = "<" + location + ">;";
//        if (sb.toString().isEmpty()) {
//            sb.append(location);
//        }
//       return sb.toString();

        return links;
    }
    
    /**
     * Return a string like : "http://myabsolutepathserver:xxxx/myentitylocationrelativepath".
     * This will added to header with X-OCCI-Location name field.
     * @param entity
     * @return 
     */
    private String renderXOCCILocationAttr(final Entity entity) {
        StringBuilder sb = new StringBuilder();
        String location = ConfigurationManager.getLocation(entity);
        
        String absoluteLocation = getServerURI().toString() + location;
        return absoluteLocation;
    }
    
    /**
     * Render attributes used for GET request on entity.
     * @param entity
     * @return 
     */
    private String renderAttributes(Entity entity) {
        String attribs = "";
        StringBuilder sb = new StringBuilder();
        List<AttributeState> attrStates = entity.getAttributes();
        String coreId = Constants.OCCI_CORE_ID + "=\"" + Constants.URN_UUID_PREFIX + entity.getId() + "\",";
         sb.append(coreId);
        if(entity instanceof Link) {
			Link link = (Link)entity;
            String source = Constants.OCCI_CORE_SOURCE + "=\"" + ConfigurationManager.getLocation(link.getSource()) + ", \"";
            sb.append(source);
            String target = Constants.OCCI_CORE_TARGET + "=\"" + ConfigurationManager.getLocation(link.getTarget()) + ", \"";
            sb.append(target);
		}
        
		for(AttributeState attribute : attrStates) {
            String name = attribute.getName();
            if (name.equals(Constants.OCCI_CORE_ID) || name.equals(Constants.OCCI_CORE_SOURCE) || name.equals(Constants.OCCI_CORE_TARGET)) {
                continue;
            }
            // if value is null, it wont display in header.
            String value = attribute.getValue();
            if (value == null) {
                continue;
            }
            
            // Used only to define the datatype.
            // TODO : Export this to a method in configuration manager getEAttributeType(AttributeState attr) method.
            String eAttributeName = Occi2Ecore.convertOcciAttributeName2EcoreAttributeName(attribute.getName());
            final EStructuralFeature eStructuralFeature = entity.eClass().getEStructuralFeature(eAttributeName);
            EDataType eAttributeType = null;
            
            if (eStructuralFeature != null) {
                
                if((eStructuralFeature instanceof EAttribute)) {
                    // Obtain the attribute type.
                    eAttributeType = ((EAttribute)eStructuralFeature).getEAttributeType();
                }
                
                if (eStructuralFeature instanceof EAttribute && eAttributeType != null && (eAttributeType instanceof EEnum || eAttributeType.getInstanceClass() == String.class)) {
                    // value with quote only for String and EEnum type.
                    value = "\"" + value + "\"";   
                } else {
                    if (eAttributeType == null) {
                        // Cant determine the type.
                        value = "\"" + value + "\"";
                    } // other values are not quoted.
                }
                
            } else {
                // Default to quoted value.
                value = "\"" + value + "\"";
            }
            
			attribs += attribute.getName() + '=' + value + ",";
		}
        
        if (!attribs.isEmpty()) {
            // To remove the last comma.
            attribs = attribs.substring(0, attribs.length() - 1);
            sb.append(attribs);
        }
        return sb.toString();
    }
    
    
}