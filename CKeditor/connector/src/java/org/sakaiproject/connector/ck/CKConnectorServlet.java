/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/ckconnect/trunk/CKeditor/connector/src/java/org/sakaiproject/connector/ck/CKConnectorServlet.java $
 * $Id: CKConnectorServlet.java 12351 2015-12-23 21:11:41Z ggolden $
 ***********************************************************************************
 *
 * Copyright (c) 2006, 2012, 2015 The Sakai Foundation.
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

package org.sakaiproject.connector.ck;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.mail.internet.MimeUtility;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileItemFactory;
import org.apache.commons.fileupload.disk.DiskFileItem;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.io.IOUtils;
import org.etudes.mneme.api.AttachmentService;
import org.sakaiproject.authz.api.SecurityAdvisor;
import org.sakaiproject.authz.cover.SecurityService;
import org.sakaiproject.component.cover.ComponentManager;
import org.sakaiproject.component.cover.ServerConfigurationService;
import org.sakaiproject.content.api.ContentCollection;
import org.sakaiproject.content.api.ContentCollectionEdit;
import org.sakaiproject.content.api.ContentResource;
import org.sakaiproject.content.api.ContentResourceEdit;
import org.sakaiproject.content.cover.ContentHostingService;
import org.sakaiproject.entity.api.Entity;
import org.sakaiproject.entity.api.Reference;
import org.sakaiproject.entity.api.ResourceProperties;
import org.sakaiproject.entity.api.ResourcePropertiesEdit;
import org.sakaiproject.event.cover.NotificationService;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.exception.IdUsedException;
import org.sakaiproject.exception.PermissionException;
import org.sakaiproject.tool.cover.SessionManager;
import org.sakaiproject.util.StringUtil;
import org.sakaiproject.util.Validator;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Conenctor Servlet to upload and browse files to a Sakai worksite for the CK editor.<br>
 * 
 * This servlet accepts 4 commands used to retrieve and create files and folders from a worksite resource. The allowed commands are:
 * <ul>
 * <li>GetFolders: Retrive the list of directory under the current folder
 * <li>GetFoldersAndFiles: Retrive the list of files and directory under the current folder
 * <li>CreateFolder: Create a new directory under the current folder
 * <li>FileUpload: Send a new file to the server (must be sent with a POST)
 * </ul>
 * 
 * The current user must have a valid sakai session with permissions to access the realm associated with the resource.
 * 
 * @author Joshua Ryan (joshua.ryan@asu.edu) merged servlets and Sakai-ified them
 * 
 *         This connector is loosely based on two servlets found on the FCK website http://www.fckeditor.net/ written by Simone Chiaretta (simo@users.sourceforge.net)
 * 
 */

public class CKConnectorServlet extends HttpServlet
{
	private static final String CK_ADVISOR_BASE = "ck.security.advisor.";
	public static final String DATE_FORMAT_NOW = "yyyyMMddHHmm";
	private static long MB_CONVERSION = 1048576;
	private static final int MAX_BUFFER_SIZE = 1024;
	protected boolean enabled;
	protected String licenseName;
	protected String licenseKey;
	protected Map<String, ResourceType> types;


	/**
	 * Manage the Get requests (GetFolders, GetFoldersAndFiles, CreateFolder).<br>
	 * 
	 * The servlet accepts commands sent in the following format:<br>
	 * connector?command=CommandName&type=ResourceType<br>
	 * <br>
	 * It executes the command and then return the results to the client in XML format. It retrieves the collection id information from the session
	 * 
	 * Valid values for Type are: Image, File, Flash and Link
	 * 
	 */
	public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
	{
		String commandStr = request.getParameter("command");
		String type = request.getParameter("type");
		String subFolder = request.getParameter("currentFolder");
		String currentFolder = (String) SessionManager.getCurrentSession().getAttribute("ck.collectionId");
		
		SecurityAdvisor advisor = (SecurityAdvisor) SessionManager.getCurrentSession().getAttribute(CK_ADVISOR_BASE + currentFolder);
		if (advisor != null)
		{
			SecurityService.pushAdvisor(advisor);
		}
		
		if (subFolder != null && !subFolder.equals("/"))
		{
			currentFolder = currentFolder + subFolder.substring(1,subFolder.length());
		}
		Document document = null;
		
		// get the URL to the current folder, without the transport, server DNS, port, etc
		
		if ("Init".equals(commandStr))
		{
			document = getDocument();
			getInit(document, type, currentFolder);
			printXML(document, response);
		}
		if ("GetFolders".equals(commandStr))
		{
			document = getDocument();
			Node root = createCommonXml(document, commandStr, type, currentFolder);

			getFolders(currentFolder, root, document);
			printXML(document, response);
		}
		else if ("GetFoldersAndFiles".equals(commandStr))
		{
			document = getDocument();
			Node root = createCommonXml(document, commandStr, type, currentFolder);

			getFolders(currentFolder, root, document);
			getFiles(currentFolder, root, document, type);
			printXML(document, response);
		}
		else if ("GetFiles".equals(commandStr))
		{
			document = getDocument();
			Node root = createCommonXml(document, commandStr, type, currentFolder);

			getFiles(currentFolder, root, document, type);
			printXML(document, response);
		}
		else if ("CreateFolder".equals(commandStr))
		{
			document = getDocument();
			Node root = createCommonXml(document, commandStr, type, currentFolder);

			String newFolderStr = request.getParameter("NewFolderName");
			String status = "110";

			try
			{
				ContentCollectionEdit edit = ContentHostingService.addCollection(currentFolder + Validator.escapeResourceName(newFolderStr)
						+ Entity.SEPARATOR);
				ResourcePropertiesEdit resourceProperties = edit.getPropertiesEdit();
				resourceProperties.addProperty(ResourceProperties.PROP_DISPLAY_NAME, newFolderStr);

				String altRoot = getAltReferenceRoot(currentFolder);
				if (altRoot != null) resourceProperties.addProperty(ContentHostingService.PROP_ALTERNATE_REFERENCE, altRoot);

				ContentHostingService.commitCollection(edit);
				status = "0";
			}
			catch (IdUsedException iue)
			{
				status = "101";
			}
			catch (PermissionException sex)
			{
				status = "103";
			}
			catch (Exception e)
			{
				status = "102";
			}
			setCreateFolderResponse(status, root, document);
			printXML(document, response);
		}
		else if ("DownloadFile".equals(commandStr))
		{
			downloadFile(request, response, currentFolder);
		}
		
		if (advisor != null)
		{
			SecurityService.clearAdvisors();
		}

	}
	
	/**
	 * Gets the document object
	 */
	private Document getDocument()
	{
		Document document = null;
		try
		{
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			DocumentBuilder builder = factory.newDocumentBuilder();
			document = builder.newDocument();
		}
		catch (ParserConfigurationException pce)
		{
			pce.printStackTrace();
		}
		return document;
	}
	
	/**
	 * Prints the response in XML format that renders via CKFinder in the browser
	 */
	private void printXML(Document document, HttpServletResponse response)
	{
		if (document == null) return;
		response.setContentType("text/xml; charset=UTF-8");
		response.setHeader("Cache-Control", "no-cache");
		
		try
		{
			OutputStream out = response.getOutputStream();

			document.getDocumentElement().normalize();
			
			StringWriter stw = new StringWriter();
			TransformerFactory tFactory = TransformerFactory.newInstance();
			Transformer transformer = tFactory.newTransformer();

			DOMSource source = new DOMSource(document);

			StreamResult result = new StreamResult(stw);
			transformer.transform(source, result);
			out.write(stw.toString().getBytes("UTF-8"));
			out.flush();
			out.close();
		}
		catch (Exception ex)
		{
			ex.printStackTrace();
		}
	}
	
	private String getCurrentFolderUrl(String currentFolder)
	{
		if (currentFolder == null) return null;
		String curFolderUrl = ContentHostingService.getUrl(currentFolder);
		int pos = curFolderUrl.indexOf("/access");
		if (pos != -1) curFolderUrl = curFolderUrl.substring(pos);
		return curFolderUrl;
	}

	/**
	 * Manage the Post requests (FileUpload).<br>
	 * 
	 * The servlet accepts commands sent in the following format:<br>
	 * connector?Command=FileUpload&Type=ResourceType<br>
	 * <br>
	 * It stores the file (renaming it in case a file with the same name exists) and then return an HTML file with a javascript command in it.
	 * 
	 */
	public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
	{

		ContentResource attachment = null;
		Reference ref = null;
		response.setContentType("text/plain; charset=UTF-8");
		// response.setHeader("Cache-Control", "no-cache");
		OutputStream out = response.getOutputStream();
	
		String command = request.getParameter("command");

		String type = request.getParameter("type");
		String subFolder = request.getParameter("currentFolder");
		String currentFolder = (String) SessionManager.getCurrentSession().getAttribute("ck.collectionId");
		String siteId = (String) SessionManager.getCurrentSession().getAttribute("ck.siteId");
		String submissionId = (String) SessionManager.getCurrentSession().getAttribute("ck.submissionId");
		SecurityAdvisor advisor = (SecurityAdvisor) SessionManager.getCurrentSession().getAttribute(CK_ADVISOR_BASE + currentFolder);
		
		boolean callFromJforum = false;
		if (advisor != null)
		{
			SecurityService.pushAdvisor(advisor);
		}

		if (subFolder != null && !subFolder.equals("/"))
		{
			currentFolder = currentFolder + subFolder.substring(1,subFolder.length());
		}
		
		
		String fileName = "";
		String errorMessage = "";

		String status = "0";
		
		if (!"FileUpload".equals(command) && !"QuickUpload".equals(command) && !"CreateFolder".equals(command) && !"DeleteFiles".equals(command)
				&& !"QuickUploadAttachment".equals(command))
		{
			status = "203";
		}
		else
		{
			if (("CreateFolder").equals(command))
			{
				// Node root = createCommonXml(document, commandStr, type, currentFolder, curFolderUrl);

				String newFolderStr = request.getParameter("NewFolderName");
				status = "110";

				try
				{
					ContentCollectionEdit edit = ContentHostingService.addCollection(currentFolder + Validator.escapeResourceName(newFolderStr)
							+ Entity.SEPARATOR);
					ResourcePropertiesEdit resourceProperties = edit.getPropertiesEdit();
					resourceProperties.addProperty(ResourceProperties.PROP_DISPLAY_NAME, newFolderStr);

					String altRoot = getAltReferenceRoot(currentFolder);
					if (altRoot != null) resourceProperties.addProperty(ContentHostingService.PROP_ALTERNATE_REFERENCE, altRoot);

					ContentHostingService.commitCollection(edit);
					status = "0";
				}
				catch (IdUsedException iue)
				{
					status = "101";
				}
				catch (PermissionException sex)
				{
					status = "103";
				}
				catch (Exception e)
				{
					status = "102";
				}
				// setCreateFolderResponse(status, root, document);
			}
			else if (("DeleteFiles").equals(command))
			{
				Document document = getDocument();
				Node root = createCommonXml(document, command, type, currentFolder);
				int i = 0;
				String paramName = "files[" + i + "][name]";
				while (request.getParameter(paramName) != null)
				{
					fileName = request.getParameter(paramName);

					ContentResourceEdit edit = null;
					if (fileName != null && fileName.trim().length() > 0)
					{
						try
						{
							edit = ContentHostingService.editResource(currentFolder + fileName);
							if (edit != null) 
							{
								ContentHostingService.removeResource(edit);
								Element deletedEl = document.createElement("DeletedFile");
								root.appendChild(deletedEl);
								deletedEl.setAttribute("name", fileName);
							}
							edit = null;
						}
						catch (IdUnusedException iue)
						{
							// status = "101";
						}
						catch (PermissionException sex)
						{
							status = "103";
						}
						catch (Exception e)
						{
							status = "102";
						}
						finally
						{
							if (edit != null) ContentHostingService.cancelResource(edit);
						}
					}
					i++;
					paramName = "files[" + (i) + "][name]";
				}
				printXML(document, response);
			}
			else
			{
				DiskFileItemFactory factory = new DiskFileItemFactory();
				factory.setSizeThreshold(30 * 1024);
				ServletFileUpload upload = new ServletFileUpload(factory);

				String encoding = request.getCharacterEncoding();
				if ((encoding != null) && (encoding.length() > 0)) upload.setHeaderEncoding(encoding);

//				System.out.println("CKConnect: tracker: " + factory.getFileCleaningTracker() + " threshold: " + factory.getSizeThreshold() + " repo: "
//						+ factory.getRepository() + " fileSizeMax: " + upload.getFileSizeMax() + " sizeMax: " + upload.getSizeMax());

				InputStream requestStream = null;
				String mime = "";
				String nameWithoutExt = "";
				String ext = "";
				FileItem uplFile = null;
				try
				{
					if ("QuickUploadAttachment".equals(command))
					{
						// QuickUploadAttachment = audio recording
						requestStream = request.getInputStream();
						mime = request.getHeader("Content-Type");
						// If there's no filename, make a guid name with the mime extension?
						if ("".equals(fileName))
						{
							fileName = UUID.randomUUID().toString() + ".wav";
						}
					}
					else
					{
						List<FileItem> items = upload.parseRequest(request);

						Map fields = new HashMap();

						Iterator iter = items.iterator();
						while (iter.hasNext())
						{
							FileItem item = (FileItem) iter.next();
							if (item.isFormField())
							{
//								System.out.println("CKConnect: isFormField: " + item.getFieldName() + " : inMemory: " + item.isInMemory() + " size: "
//										+ item.getSize());

								fields.put(item.getFieldName(), item.getString());
								
								// clean up if a temp file was used
								if (!item.isInMemory())
								{
									item.delete();
								}								
							}
							else
							{
//								System.out.println("CKConnect: file: " + ((DiskFileItem) item).getStoreLocation().getPath() + " : " + item.getFieldName()
//										+ " : inMemory: " + item.isInMemory() + " size: " + item.getSize());

								fields.put(item.getFieldName(), item);
							}
						}

						uplFile = (FileItem) fields.get("upload");

						if (uplFile != null)
						{
							String filePath = uplFile.getName();
							filePath = filePath.replace('\\', '/');
							String[] pathParts = filePath.split("/");
							fileName = pathParts[pathParts.length - 1];
							mime = uplFile.getContentType();
						}
					}

					nameWithoutExt = fileName;
					ext = "";

					if (fileName.lastIndexOf(".") > 0)
					{
						nameWithoutExt = fileName.substring(0, fileName.lastIndexOf("."));
						ext = fileName.substring(fileName.lastIndexOf("."));
					}

					int counter = 1;
					boolean done = false;

					while (!done)
					{
						try
						{
							ResourcePropertiesEdit resourceProperties = ContentHostingService.newResourceProperties();
							resourceProperties.addProperty(ResourceProperties.PROP_DISPLAY_NAME, fileName);

							if ("QuickUploadAttachment".equals(command))
							{
								// For Jforum, put file in FS..all others go to CH
								if (!currentFolder.contains("sakai_jforum"))
								{
									// If submissionId is not null, call is from Mneme student audio recording
									if (submissionId == null)
									{
										String altRoot = getAltReferenceRoot(currentFolder);
										if (altRoot != null) resourceProperties.addProperty(ContentHostingService.PROP_ALTERNATE_REFERENCE, altRoot);

										int noti = NotificationService.NOTI_NONE;
										if (requestStream != null)
											attachment = ContentHostingService.addResource(currentFolder + fileName, mime,
													IOUtils.toByteArray(requestStream), resourceProperties, noti);
									}
									else
									{
										if (siteId != null)
										{
											AttachmentService attachmentService = null;
											try
											{
												attachmentService = getAttachmentService();
											}
											catch (Exception e)
											{
												e.printStackTrace();
											}
											ref = attachmentService.addAttachment(siteId, submissionId, fileName,
													IOUtils.toByteArray(requestStream), "audio/x-wav");
										}
									}
								}
								else
								{
									try
									{
										currentFolder = currentFolder.substring(12,currentFolder.length());
										new File(currentFolder).mkdirs();
										saveAttachmentFile(currentFolder + File.separator + fileName, requestStream);
										callFromJforum = true;
									}
									catch (Exception e)
									{
										e.printStackTrace();
									}
								}
							}
							else
							{
								String altRoot = getAltReferenceRoot(currentFolder);
								if (altRoot != null) resourceProperties.addProperty(ContentHostingService.PROP_ALTERNATE_REFERENCE, altRoot);

								int noti = NotificationService.NOTI_NONE;
								ContentHostingService.addResource(currentFolder + fileName, mime, uplFile.get(), resourceProperties, noti);
							}
							done = true;
						}
						catch (IdUsedException iue)
						{
							// the name is already used, so we do a slight rename to prevent the colision
							fileName = nameWithoutExt + "(" + counter + ")" + ext;
							status = "201";
							counter++;
						}

						catch (Exception ex)
						{
							// this user can't write where they are trying to write.
							done = true;
							ex.printStackTrace();
							status = "203";
						}
					}
				}
				catch (Exception ex)
				{
					ex.printStackTrace();
					status = "203";
				}
				finally
				{
					if (uplFile != null)
					{
						if (uplFile instanceof DiskFileItem)
						{
							DiskFileItem dfi = (DiskFileItem) uplFile;
							if (!dfi.isInMemory())
							{
								dfi.delete();
							}
						}
						
					}
				}

				if ("QuickUpload".equals(command))
				{
					out.write("<script type=\"text/javascript\">".getBytes("UTF-8"));
					/*
					 * out.write("window.parent.OnUploadCompleted(" + status + ",'" + ContentHostingService.getUrl(currentFolder) + fileName + "','" + fileName + "','" + errorMessage + "');");
					 */
				}
				else
				{

					if ("QuickUploadAttachment".equals(command))
					{
						if (attachment != null)
						{
							out.write(attachment.getUrl().getBytes("UTF-8"));
						}
						else
						{
							//For Mneme student submissions
							if (ref != null)
							{
								out.write(ref.getUrl().getBytes("UTF-8"));
							}
							if (callFromJforum)
							{
								String url = "/portal/tool/" + (String) SessionManager.getCurrentSession().getAttribute("ck.toolId")
										+ "/processCKEAudio/" + fileName;
								
								out.write(url.getBytes("UTF-8"));
							}
						}
					}
					else
					{
						out.write("<script type=\"text/javascript\">".getBytes("UTF-8"));
						// out.write((this.newFileName + "|" + errorMsg).getBytes("UTF-8"));
						out.write((fileName + "|").getBytes("UTF-8"));
					}
				}
			}
		}
		out.flush();
		out.close();

		if (advisor != null)
		{
			SecurityService.clearAdvisors();
		}
	}

	/**
	 * save attachment file
	 * @param filename
	 * @param inputStream
	 * @throws Exception
	 */
	private void saveAttachmentFile(String filename, InputStream fis) throws Exception
	{
		//FileInputStream fis = null;
		FileOutputStream outputStream = null;
		
		try {
			//fis = new FileInputStream(inputStream);
			new File(filename);
			outputStream = new FileOutputStream(filename);
			
			int c = 0;
			byte[] b = new byte[4096];
			while ((c = fis.read(b)) != -1) {
				outputStream.write(b, 0, c);
			}
		}catch (Exception e){
			e.printStackTrace();
		}
		finally {
			if (outputStream != null) {
				outputStream.flush();
				outputStream.close();
			}
			
			if (fis != null) {
				fis.close();
			}
		}
	}
	
	private void setCreateFolderResponse(String status, Node root, Document doc)
	{
		Element element = doc.createElement("Error");
		element.setAttribute("number", status);
		root.appendChild(element);
	}

	/**
	 * Fetches the folders that appear in CKFinder
	 */
	private void getFolders(String currentFolder, Node root, Document doc)
	{
		if (currentFolder == null) return;
		Element folders = doc.createElement("Folders");
		root.appendChild(folders);

		ContentCollection collection = null;

		Map map = null;
		Iterator foldersIterator = null;

		try
		{
			// hides the real root level stuff and just shows the users the
			// the root folders of all the top collections they actually have access to.
			if (currentFolder.split("/").length == 2)
			{
				List collections = new ArrayList();
				map = ContentHostingService.getCollectionMap();
				if (map != null && map.keySet() != null)
				{
					collections.addAll(map.keySet());
				}
				
				foldersIterator = collections.iterator();
			}
			else if (currentFolder.split("/").length > 2)
			{
				collection = ContentHostingService.getCollection(currentFolder);
				if (collection != null && collection.getMembers() != null) 
				{
					foldersIterator = collection.getMembers().iterator();
				}
			}
		}
		catch (IdUnusedException ie)
		{
			//Do nothing - SAK-472
		}
		catch (Exception e)
		{  
			e.printStackTrace();
			// not a valid collection? file list will be empty and so will the doc
		}
		if (foldersIterator != null)
		{
			String current = null;

			while (foldersIterator.hasNext())
			{
				try
				{
					current = (String) foldersIterator.next();
					ContentCollection myCollection = ContentHostingService.getCollection(current);
					Element element = doc.createElement("Folder");
					element.setAttribute("url", current);
					element.setAttribute("name", myCollection.getProperties().getProperty(myCollection.getProperties().getNamePropDisplayName()));
					//ACL values set to ensure context menu shows certain options disabled
					element.setAttribute("acl", "177");
					String hasChildren = "true";
					if (myCollection.getMemberCount() == 0) hasChildren = "false";
					element.setAttribute("hasChildren", hasChildren);
					folders.appendChild(element);
				}
				catch (Exception e)
				{
					// do nothing, we either don't have access to the collction or it's a resource
				}
			}
		}
	}

	/**
	 * Shows list of files in collection
	 */
	private void getFiles(String currentFolder, Node root, Document doc, String type)
	{
		if (currentFolder == null) return;
		Element files = doc.createElement("Files");
		root.appendChild(files);

		ContentCollection collection = null;
		
		try
		{
			collection = ContentHostingService.getCollection(currentFolder);
		}
		catch (Exception e)
		{
			// do nothing, file will be empty and so will doc
		}
		if (collection != null)
		{
			Iterator iterator = collection.getMemberResources().iterator();

			while (iterator.hasNext())
			{
				try
				{
					ContentResource current = (ContentResource) iterator.next();

					String ext = current.getProperties().getProperty(current.getProperties().getNamePropContentType());
					if ((type.equals("Files") && (ext != null) && (!ext.equals("text/url"))) || (type.equals("Images") && ext.startsWith("image"))
							|| (type.equals("Flash") && ext.equalsIgnoreCase("application/x-shockwave-flash")))
					{

						String id = current.getId();

						Element element = doc.createElement("File");
						// displaying the id instead of the display name because the url used
						// for linking in the CK editor uses what is returned...
						element.setAttribute("name", current.getProperties().getProperty(current.getProperties().getNamePropDisplayName()));
						// the folder is added, so just get the name part of the URL
						String nameUrl = id.substring(id.lastIndexOf("/") + 1);
						// escape for HTML
						nameUrl = Validator.escapeUrl(nameUrl);
						// element.setAttribute("url", nameUrl);
						Calendar cal = Calendar.getInstance();
						SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT_NOW);

						// element.setAttribute("date", sdf.format(cal.getTime()));
						element.setAttribute("date", current.getProperties().getProperty(current.getProperties().PROP_CREATION_DATE));
						if (current.getProperties().getProperty(current.getProperties().getNamePropContentLength()) != null)
						{
							long size = current.getProperties().getLongProperty(current.getProperties().getNamePropContentLength());
							if (size < 1024) element.setAttribute("size","1");
							else element.setAttribute("size", String.valueOf((int)Math.round(size/1024)));
						}
						else
						{
							element.setAttribute("size", "0");

						}
						files.appendChild(element);
					}
				}
				catch (ClassCastException e)
				{
					// it's a colleciton not an item
				}
				catch (Exception e)
				{
					// do nothing, we don't have access to the item
				}
			}
		}
	}
	
	/**
	 * Allows user to download file
	 */
	private void downloadFile(HttpServletRequest request, HttpServletResponse response, String currentFolder)
	{
		File file = null;
		String fileName = null;
		Object format = null;
		String newFileName;
		
		fileName = getParameter(request, "FileName");
		if (fileName == null || currentFolder == null) return;
		// problem with showing filename when dialog window appear
		newFileName = request.getParameter("FileName").replaceAll("\"", "\\\\\"");

		try
		{
			if (request.getHeader("User-Agent").indexOf("MSIE") != -1)
			{
				newFileName = URLEncoder.encode(newFileName, "UTF-8");
				newFileName = newFileName.replace("+", " ").replace("%2E", ".");
			}
			else
			{
				newFileName = MimeUtility.encodeWord(newFileName, "utf-8", "Q");
			}
		}
		catch (UnsupportedEncodingException ex)
		{
		}
		String mimetype = getServletContext().getMimeType(fileName);
		response.setCharacterEncoding("utf-8");
		if (format != null && format.equals("text"))
		{
			response.setContentType("text/plain; charset=utf-8");
		}
		else
		{
			if (mimetype != null)
			{
				response.setContentType(mimetype);
			}
			else
			{
				response.setContentType("application/octet-stream");
			}
			if (file != null)
			{
				response.setContentLength((int) file.length());
			}

			response.setHeader("Content-Disposition", "attachment; filename=\"" + newFileName + "\"");
		}

		response.setHeader("Cache-Control", "cache, must-revalidate");
		response.setHeader("Pragma", "public");
		response.setHeader("Expires", "0");
		try
		{
			ContentResource cr = ContentHostingService.getResource(currentFolder + fileName);
			if (cr != null)
			{
				/*
				 * file = new File(configuration.getTypes().get(type).getPath() + currentFolder, fileName);
				 */

				/*
				 * if (!AccessControlUtil.getInstance(configuration).checkFolderACL( type, currentFolder, userRole, AccessControlUtil.CKFINDER_CONNECTOR_ACL_FILE_VIEW)) { throw new ConnectorException(
				 * Constants.Errors.CKFINDER_CONNECTOR_ERROR_UNAUTHORIZED); }
				 */

				// file = new File(cr.getContent());

				try
				{
					printStreamContentToResponse(cr.streamContent(), response.getOutputStream());
					// printFileContentToResponse(file, response.getOutputStream());
					response.getOutputStream().flush();
					response.getOutputStream().close();
				}
				catch (IOException e)
				{
					e.printStackTrace();
					/*
					 * throw new ConnectorException( Constants.Errors.CKFINDER_CONNECTOR_ERROR_ACCESS_DENIED, e);
					 */
				}
				
				/*
				 * if (!FileUtils.checkFileName(fileName) || FileUtils.checkFileExtension(fileName, configuration.getTypes().get(type), configuration, false) == 1) { throw new ConnectorException( Constants.Errors.CKFINDER_CONNECTOR_ERROR_INVALID_REQUEST);
				 * }
				 * 
				 * if (FileUtils.checkIfDirIsHidden(currentFolder, configuration)) { throw new ConnectorException( Constants.Errors.CKFINDER_CONNECTOR_ERROR_INVALID_REQUEST); } try { if (!file.exists() || !file.isFile() ||
				 * FileUtils.checkIfFileIsHidden(fileName, configuration)) { throw new ConnectorException( Constants.Errors.CKFINDER_CONNECTOR_ERROR_FILE_NOT_FOUND); }
				 * 
				 * FileUtils.printFileContentToResponse(file, out); } catch (IOException e) { throw new ConnectorException( Constants.Errors.CKFINDER_CONNECTOR_ERROR_ACCESS_DENIED, e); }
				 */

			}
		}
		catch (Exception e)
		{
			// do nothing, file will be empty and so will doc
		}

	}

	protected String getParameter(final HttpServletRequest request, final String paramName)
	{
		if (request.getParameter(paramName) == null)
		{
			return null;
		}
		return request.getParameter(paramName);
		/*
		 * return convertFromUriEncoding( request.getParameter(paramName), configuration);
		 */
	}

	/*
	 * public String convertFromUriEncoding(final String fileName, final IConfiguration configuration) { try { return new String(fileName.getBytes(configuration.getUriEncoding()), "UTF-8"); } catch (UnsupportedEncodingException e) { return fileName; } }
	 */
	
	/**
	 * Print file input stream to outputstream.
	 * @param instream input stream to be printed.
	 * @param out outputstream.
	 * @throws IOException when io error occurs.
	 */
	public static void printStreamContentToResponse(final InputStream instream, final OutputStream out) throws IOException
	{
		InputStream in = null;

		try
		{
			in = instream;
			byte[] buf = null;
			buf = new byte[MAX_BUFFER_SIZE];
			int numRead = 0;
			while ((numRead = in.read(buf)) != -1)
			{
				out.write(buf, 0, numRead);
			}
		}
		catch (IOException e)
		{
			throw e;
		}
		finally
		{
			try
			{
				if (in != null)
				{
					in.close();
				}
			}
			catch (IOException e)
			{
				throw e;
			}
		}
	}
	
	/**
	 * Print file content to outputstream.
	 * @param file file to be printed.
	 * @param out outputstream.
	 * @throws IOException when io error occurs.
	 */
	/*public static void printFileContentToResponse(final File file, final OutputStream out) throws IOException
	{
		FileInputStream in = null;
		InputStream in = null;
		if (file.length() == 0)
		{
			return;
		}
		try
		{
			in = new FileInputStream(file);
			in = file;
			byte[] buf = null;
			if (file.length() < MAX_BUFFER_SIZE)
			{
				buf = new byte[(int) file.length()];
			}
			else
			{
				buf = new byte[MAX_BUFFER_SIZE];
			}

			int numRead = 0;
			while ((numRead = in.read(buf)) != -1)
			{
				out.write(buf, 0, numRead);
			}
		}
		catch (IOException e)
		{
			throw e;
		}
		finally
		{
			try
			{
				if (in != null)
				{
					in.close();
				}
			}
			catch (IOException e)
			{
				throw e;
			}
		}
	}*/
	
	
	public void init() throws ServletException
	{
		try
		{
			initFile();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	private Node getInit(Document doc, String type, String currentFolder)
	{
		Element rtEl;

		String curFolderUrl = getCurrentFolderUrl(currentFolder);
		
		long maxSize = Long.parseLong(ServerConfigurationService.getString("content.upload.max", "1")) * MB_CONVERSION;

		Element root = doc.createElement("Connector");
		doc.appendChild(root);

		Element errEl = doc.createElement("Error");
		errEl.setAttribute("number", "0");
		root.appendChild(errEl);

		Element ciEl = doc.createElement("ConnectorInfo");
		ciEl.setAttribute("enabled", String.valueOf(this.enabled));
		ciEl.setAttribute("imgWidth", "");
		ciEl.setAttribute("imgHeight", "");
		ciEl.setAttribute("s", this.licenseName);
		ciEl.setAttribute("c", this.licenseKey);
		ciEl.setAttribute("thumbsEnabled", "false");
		ciEl.setAttribute("thumbsUrl", "");
		ciEl.setAttribute("thumbsDirectAccess", "false");
		root.appendChild(ciEl);

		Element rtsEl = doc.createElement("ResourceTypes");
		root.appendChild(rtsEl);
		
		if ((this.types != null)&&(this.types.size() > 0))
		{
			if (this.types.get(type) != null)
			{
				//Properties of a resource type(Files, Images and Flash) can be configured in the settings xml
				ResourceType rt = (ResourceType) this.types.get(type);
				rtEl = doc.createElement("ResourceType");
				rtEl.setAttribute("name", rt.getName());
				rtEl.setAttribute("url", curFolderUrl);
				rtEl.setAttribute("allowedExtensions", rt.getAllowedExtensions());
				rtEl.setAttribute("deniedExtensions", rt.getDeniedExtensions());
				rtEl.setAttribute("maxSize", String.valueOf(maxSize));
				rtEl.setAttribute("defaultView", "list");
				rtEl.setAttribute("hash", "4d8ddfd385d0952b");
				rtEl.setAttribute("hasChildren", "true");
				rtEl.setAttribute("acl", "177");
				rtsEl.appendChild(rtEl);
			}
		}
		return root;

	}

	private Node createCommonXml(Document doc, String commandStr, String type, String currentFolder)
	{
		String curFolderUrl = getCurrentFolderUrl(currentFolder);
		Element root = doc.createElement("Connector");
		doc.appendChild(root);
		// root.setAttribute("command", commandStr);
		root.setAttribute("resourceType", type);

		Element errEl = doc.createElement("Error");
		errEl.setAttribute("number", "0");
		root.appendChild(errEl);

		Element element = doc.createElement("CurrentFolder");
		element.setAttribute("path", currentFolder);
		element.setAttribute("url", curFolderUrl);
		element.setAttribute("acl", "255");
		root.appendChild(element);

		return root;

	}

	private String getAltReferenceRoot(String id)
	{
		String altRoot = null;
		try
		{
			altRoot = StringUtil.trimToNull(ContentHostingService.getProperties(id).getProperty(ContentHostingService.PROP_ALTERNATE_REFERENCE));
		}
		catch (Exception e)
		{
			// do nothing, we either didn't have permission or the id is bogus
		}
		if (altRoot != null && !"/".equals(altRoot) && !"".equals(altRoot))
		{
			if (!altRoot.startsWith(Entity.SEPARATOR)) altRoot = Entity.SEPARATOR + altRoot;
			if (altRoot.endsWith(Entity.SEPARATOR)) altRoot = altRoot.substring(0, altRoot.length() - Entity.SEPARATOR.length());
			return altRoot;
		}
		else
			return null;
	}
	
	/**
	 * initialize configuration from XML config file.
	 *
	 * @throws Exception
	 *             when error occurs.
	 */
	public void initFile() throws Exception {
		clearConfiguration();
		//this.loading = true;
		String configPath = getFullConfigPath();
		if (configPath != null)
		{
			File file = new File(getFullConfigPath());
			// this.lastCfgModificationDate = file.lastModified();
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			DocumentBuilder db = dbf.newDocumentBuilder();
			Document doc = db.parse(file);
			doc.normalize();
			Node node = doc.getFirstChild();
			if (node != null)
			{
				NodeList nodeList = node.getChildNodes();
				for (int i = 0; i < nodeList.getLength(); i++)
				{
					Node childNode = nodeList.item(i);
					if (childNode.getNodeName().equals("enabled") && childNode.getFirstChild() != null)
					{
						this.enabled = Boolean.valueOf(childNode.getFirstChild().getNodeValue().trim());
					}
					if (childNode.getNodeName().equals("licenseName") && childNode.getFirstChild() != null)
					{
						this.licenseName = childNode.getFirstChild().getNodeValue().trim();
					}
					if (childNode.getNodeName().equals("licenseKey") && childNode.getFirstChild() != null)
					{
						this.licenseKey = childNode.getFirstChild().getNodeValue().trim();
					}
				}
			}
			setTypes(doc);
		}
		/*this.events = new Events();
		registerEventHandlers();
		this.loading = false;*/
	}
	
	/**
	 * clears all configuration values.
	 */
	private void clearConfiguration() {
		this.enabled = false;
		this.licenseName = "";
		this.licenseKey = "";
		this.types = new HashMap<String, ResourceType>();
	}

	/**
	 * Returns reference to the config settings xml file
	 */
	private String getFullConfigPath() throws Exception
	{
		File file = new File(ServerConfigurationService.getString("ckfinder.settingsxml"));

		try
		{
			if (file.exists() && file.isFile())
			{
				return file.getAbsolutePath();
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}

		return null;
	}
	
	/**
	 * creates types configuration from XML configuration.
	 *
	 * @param doc
	 *            XML document.
	 */
	private void setTypes(final Document doc) {
		types = new HashMap<String, ResourceType>();
		NodeList list = doc.getElementsByTagName("type");

		for (int i = 0; i < list.getLength(); i++) {
			Element element = (Element) list.item(i);
			String name = element.getAttribute("name");
			if (name != null && !name.equals("")) {
				ResourceType resourceType = createTypeFromXml(
						name, element.getChildNodes());
				types.put(name, resourceType);
			}

		}
	}
	
	private Map<String, ResourceType> getTypes() {
		return this.types;
	}

	/**
	 * Creates type configuration from XML.
	 *
	 * @param typeName
	 *            name of type.
	 * @param childNodes
	 *            type XML nodes.
	 * @return resource type
	 */
	private ResourceType createTypeFromXml(final String typeName,
			final NodeList childNodes) {
		ResourceType resourceType = new ResourceType(typeName);
		for (int i = 0; i < childNodes.getLength(); i++) {
			Node childNode = childNodes.item(i);
			if (childNode.getNodeName().equals("allowedExtensions") && childNode.getFirstChild() != null) {
				resourceType.setAllowedExtensions(childNode.getFirstChild().getNodeValue().trim());
			}
			if (childNode.getNodeName().equals("deniedExtensions") && childNode.getFirstChild() != null) {
				resourceType.setDeniedExtensions(childNode.getFirstChild().getNodeValue().trim());
			}
		}
		return resourceType;
	}
	
	/**
	 * @return The AttachmentService, via the component manager.
	 */
	private AttachmentService getAttachmentService()
	{
		return (AttachmentService) ComponentManager.get(AttachmentService.class);
	}	
}
