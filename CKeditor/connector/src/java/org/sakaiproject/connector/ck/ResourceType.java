/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/ckconnect/trunk/CKeditor/connector/src/java/org/sakaiproject/connector/ck/ResourceType.java $
 * $Id: ResourceType.java 4151 2013-01-08 03:13:06Z mallikamt $
 ***********************************************************************************
 *
 * Copyright (c) 2006, 2012 The Sakai Foundation.
 *
 * Licensed under the Educational Community License, Version 1.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/ecl1.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 **********************************************************************************/

/*
 * CKFinder
 * ========
 * http://ckfinder.com
 * Copyright (C) 2007-2012, CKSource - Frederico Knabben. All rights reserved.
 *
 * The software, this file and its contents are subject to the CKFinder
 * License. Please read the license.txt file before using, installing, copying,
 * modifying or distribute this file or part of its contents. The contents of
 * this file is part of the Source Code of CKFinder.
*/


package org.sakaiproject.connector.ck;

import org.w3c.dom.Node;


/**
 * Resource type entity.
 */
public class ResourceType {

	/**
	 * resource name.
	 */
	private String name;
	
	/**
	 * max file size in resource.
	 */
	private String maxSize;
	/**
	 * list of allowed extensions in resource (spepareted with comma).
	 */
	private String allowedExtensions;
	/**
	 * list of denied extensions in resource (spepareted with comma).
	 */
	private String deniedExtensions;
	/**
	 *
	 */


	/**
	 * Constructor.
	 * @param name resource type name.
	 * @param allowedExtensions allowed extensions for resource type.
	 * @param deniedExtensions denied extensions for resource type.
	 */
	public ResourceType(final String name, 
						final String allowedExtensions,
						final String deniedExtensions) {
		this.allowedExtensions = allowedExtensions;
		this.deniedExtensions = deniedExtensions;
		this.name = name;
	}

	/**
	 * contrutor.
	 * @param name type name
	 */
	public ResourceType(final String name) {
		this.name = name;
	}

	/**
	 * @return the name
	 */
	public final String getName() {
		return name;
	}



	/**
	 * @param name the name to set
	 */
	public final void setName(final String name) {
		this.name = name;
	}

	/**
	 * @return the allowedExtensions
	 */
	public final String getAllowedExtensions() {
		if (allowedExtensions == null) {
			return "";
		}
		return allowedExtensions;
	}

	/**
	 * @param allowedExtensions the allowedExtensions to set
	 */
	public final void setAllowedExtensions(final String allowedExtensions) {
		this.allowedExtensions = allowedExtensions;
	}

	/**
	 * @return the deniedExtensions
	 */
	public final String getDeniedExtensions() {
		if (deniedExtensions == null) {
			return "";
		}
		return deniedExtensions;
	}

	/**
	 * @param deniedExtensions the deniedExtensions to set
	 */
	public final void setDeniedExtensions(final String deniedExtensions) {
		this.deniedExtensions = deniedExtensions;
	}

	/**
	 * clone constuctor.
	 * @param type source type
	 */
	public ResourceType(final ResourceType type) {
		super();
		this.name = type.name;
		this.allowedExtensions = type.allowedExtensions;
		this.deniedExtensions = type.deniedExtensions;
	}
}
