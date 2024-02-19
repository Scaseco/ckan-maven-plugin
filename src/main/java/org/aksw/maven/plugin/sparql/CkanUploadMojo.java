/*
 * Copyright 2013 Luca Tagliani
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
 */
package org.aksw.maven.plugin.sparql;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.crypto.DefaultSettingsDecryptionRequest;
import org.apache.maven.settings.crypto.SettingsDecrypter;
import org.apache.maven.settings.crypto.SettingsDecryptionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;

import eu.trentorise.opendata.jackan.CkanClient;
import eu.trentorise.opendata.jackan.exceptions.CkanException;
import eu.trentorise.opendata.jackan.exceptions.CkanNotFoundException;
import eu.trentorise.opendata.jackan.internal.org.apache.http.HttpEntity;
import eu.trentorise.opendata.jackan.internal.org.apache.http.HttpResponse;
import eu.trentorise.opendata.jackan.internal.org.apache.http.client.methods.HttpPost;
import eu.trentorise.opendata.jackan.internal.org.apache.http.entity.ContentType;
import eu.trentorise.opendata.jackan.internal.org.apache.http.entity.mime.MultipartEntityBuilder;
import eu.trentorise.opendata.jackan.internal.org.apache.http.entity.mime.content.FileBody;
import eu.trentorise.opendata.jackan.internal.org.apache.http.entity.mime.content.StringBody;
import eu.trentorise.opendata.jackan.internal.org.apache.http.impl.client.CloseableHttpClient;
import eu.trentorise.opendata.jackan.internal.org.apache.http.impl.client.HttpClientBuilder;
import eu.trentorise.opendata.jackan.model.CkanDataset;
import eu.trentorise.opendata.jackan.model.CkanOrganization;
import eu.trentorise.opendata.jackan.model.CkanResource;
import eu.trentorise.opendata.jackan.model.CkanTag;

/**
 * The Maven Old Java Object (MOJO) that implements the goal for
 * uploading files to CKAN.
 */
@Mojo(name = "upload", defaultPhase = LifecyclePhase.DEPLOY)
public class CkanUploadMojo extends AbstractMojo {

    // private static final Logger logger = LoggerFactory.getLogger(CkanUploadMojo.class);

    @Parameter(defaultValue = "${settings}", readonly = true)
    private Settings settings;

    @Component
    private SettingsDecrypter decrypter;

    @Parameter(property = "ckan.url", required = true)
    private String ckanUrl;

    @Parameter(property = "ckan.serverId", required = true)
    private String serverId;

//    @Parameter(property = "ckan.apiKey", required = true)
//    private String apiKey;

    @Parameter(property = "ckan.fileName", required = true)
    private String fileName;

    @Parameter(property = "ckan.downloadFileName", required = false)
    private String downloadFileName;

    @Parameter(property = "ckan.datasetId", required = true)
    private String datasetId;

    @Parameter(property = "ckan.resourceId", defaultValue = "${project.artifactId}", required = true)
    private String resourceId;

    @Parameter(property = "ckan.organizationId", required = false)
    private String organizationId;

    @Parameter(property = "ckan.author", required = false)
    private String author;

    @Override
    public void execute() throws MojoExecutionException {
        File file = new File(fileName);
        if (!file.exists()) {
            throw new MojoExecutionException("File " + fileName + " does not exist.");
        }

        try {
            Server server = settings.getServer(serverId);

            String apiKey = null;
            if (server != null) {
                SettingsDecryptionResult result = decrypter.decrypt(new DefaultSettingsDecryptionRequest(server));
                server = result.getServer();
                // Assuming the API key is stored in the password field
                apiKey = server.getPassword();
            }

            CkanClient ckanClient = new CkanClient(ckanUrl, apiKey);
            Path path = Path.of(fileName);
            doDeploy(ckanClient, path);
        } catch (IOException e) {
            throw new MojoExecutionException("Error uploading file to CKAN", e);
        }
    }

    /**
     * Deployment of a file as a CKAN resource.
     */
    public void doDeploy(CkanClient ckanClient, Path file) throws IOException {
        Log logger = getLog();

        String datasetName = datasetId
                .replace(":", "-")
                .replace(".", "-")
                .replace(" ", "-")
                .toLowerCase()
//                .replaceAll("[0-9]", "x");
                ;

        if (logger.isInfoEnabled()) {
            logger.info("Post-processed name to " + datasetName);
        }

        CkanDataset remoteCkanDataset;

        boolean isDatasetCreationRequired = false;
        try {
            remoteCkanDataset = ckanClient.getDataset(datasetName);
        } catch(CkanNotFoundException e) {
            if (logger.isInfoEnabled()) {
                logger.info("Dataset does not yet exist");
            }
            remoteCkanDataset = new CkanDataset();
            isDatasetCreationRequired = true;
        } catch(CkanException e) {
            // TODO Maybe the dataset was deleted
            remoteCkanDataset = new CkanDataset();
            isDatasetCreationRequired = true;
        }

        CkanOrganization ckanOrg = null;

        if (organizationId != null) {
            ckanOrg = ckanClient.getOrganization(organizationId);
        }

        remoteCkanDataset.setAuthor(author);

        if (ckanOrg != null) {
            remoteCkanDataset.setOrganization(ckanOrg);
            remoteCkanDataset.setOwnerOrg(ckanOrg.getId());
        }

        if (logger.isInfoEnabled()) {
            logger.info("Is creation required? " + isDatasetCreationRequired);
        }


        // Use post processed name
//        remoteCkanDataset.setId(datasetName);
        remoteCkanDataset.setName(datasetName);


        // Append tags
        // TODO Add switch whether to overwrite instead of append
        boolean replaceTags = false; // true = appendTags

        Optional<List<CkanTag>> existingTags = Optional.ofNullable(remoteCkanDataset.getTags());

//        Optional<List<CkanTag>> newTags;
//        if(replaceTags) {
//            newTags = Optional.of(dataset.getKeywords().stream().map(CkanTag::new).collect(Collectors.toList()));
//        } else {
//            // Index existing tags by name
//            Map<String, CkanTag> nameToTag = existingTags.orElse(Collections.emptyList()).stream()
//                    .filter(tag -> tag.getVocabularyId() == null)
//                    .collect(Collectors.toMap(CkanTag::getName, x -> x));
//
//            // Allocate new ckan tags objects for non-covered keywords
//            List<CkanTag> addedTags = dataset.getKeywords().stream()
//                    .filter(keyword -> !nameToTag.containsKey(keyword))
//                    .map(CkanTag::new)
//                    .collect(Collectors.toList());
//
//            // If there was no change, leave the original value (whether null or empty list)
//            // Otherwise, reuse the existing tag list or allocate a new one
//            newTags = addedTags.isEmpty()
//                    ? existingTags
//                    : Optional.of(existingTags.orElse(new ArrayList<>()));
//
//            // If there were changes, append the added tags
//            if(newTags.isPresent()) {
//                newTags.get().addAll(addedTags);
//            }
//        }
//
//        newTags.ifPresent(remoteCkanDataset::setTags);

//		System.out.println("After: " + remoteCkanDataset);

        if(isDatasetCreationRequired) {
            remoteCkanDataset = ckanClient.createDataset(remoteCkanDataset);
        } else {
            remoteCkanDataset = ckanClient.updateDataset(remoteCkanDataset);
        }

        // String resourec = dcatDistribution.getTitle();

        if (logger.isInfoEnabled()) {
            logger.info("Deploying distribution " + resourceId);
        }



        CkanResource remoteCkanResource = createOrUpdateResource(ckanClient, remoteCkanDataset, resourceId);

//        boolean noFileUpload = false;
//        if (!noFileUpload) {
//
//            // Check if there is a graph in the dataset that matches the distribution
//            String distributionName = resourceId; // dcatDistribution.getTitle();
//
//            logger.info("Deploying distribution " + distributionName);
//
//
////                Set<URI> urlsToExistingPaths = resolvedValidUrls.stream()
////                        .filter(uri ->
////                                DcatCkanDeployUtils.pathsGet(uri)
////                                .filter(Files::exists)
////                                .filter(Files::isRegularFile)
////                                .isPresent())
////                        .collect(Collectors.toSet());
////
////                Set<URI> webUrls = Sets.difference(resolvedValidUrls, urlsToExistingPaths);
//
//            URI uri = file.toUri();
//            Set<URI> urlsToExistingPaths = Set.of(uri);
//            Set<URI> webUrls = Set.of();
//
//            String downloadFilename = null;
//            Optional<Path> pathReference = Optional.empty();
//            Path root = null;
//            if (urlsToExistingPaths.size() > 0) {
//                URI fileUrl = urlsToExistingPaths.iterator().next();
//                pathReference = pathsGet(fileUrl);
//                downloadFilename = pathReference.get().getFileName().toString();
//            } else {
//                // Assume web url - try to download locally and upload
////                    if (!webUrls.isEmpty()) {
////                        // TODO This should go through the conjure resource cache
////                        root = Files.createTempDirectory("http-cache-");
////                        URI webUrl = webUrls.iterator().next();
////                        String webUrlPathStr = webUrl.getPath();
////                        Path tmp =  Paths.get(webUrlPathStr);
////                        downloadFilename = tmp.getFileName().toString();
////
////                        HttpResourceRepositoryFromFileSystemImpl manager = HttpResourceRepositoryFromFileSystemImpl.create(root);
////
////                        BasicHttpRequest r = new BasicHttpRequest("GET", webUrl.toASCIIString());
////        //                r.setHeader(HttpHeaders.ACCEPT, WebContent.contentTypeTurtleAlt2);
////        //                r.setHeader(HttpHeaders.ACCEPT_ENCODING, "gzip,identity;q=0");
////
////                        RdfHttpEntityFile httpEntity = manager.get(r, HttpResourceRepositoryFromFileSystemImpl::resolveRequest);
////                        pathReference = Optional.ofNullable(httpEntity).map(RdfHttpEntityFile::getAbsolutePath);
////                    }
//            }
//
//            // TODO This breaks if the downloadURLs are web urls.
//            // We need a flag whether to do a file upload for web urls, or whether to just update metadata
//
////            Optional<Path> pathReference = resolvedValidUrls.stream()
////                .map(DcatCkanDeployUtils::pathsGet)
////                .filter(Optional::isPresent)
////                .map(Optional::get)
////                .filter(Files::exists)
////                .findFirst();
////

            if (file != null) {

                //String filename = distributionName + ".nt";
                String probedContentType = null;
                try {
                    probedContentType = Files.probeContentType(file);
                } catch (IOException e) {
                    if (logger.isWarnEnabled()) {
                        logger.warn("Failed to probe content type of " + file, e);
                    }
                }

                String contentType = Optional.ofNullable(probedContentType).orElse(ContentType.APPLICATION_OCTET_STREAM.toString());

                String finalDownloadFileName = downloadFileName != null
                        ? downloadFileName
                        : file.getFileName().toString();


//	                if (!noFileUpload) {

                logger.info("Uploading file " + file);
                CkanResource tmp = uploadFile(
                        ckanClient,
                        remoteCkanDataset.getName(),
                        remoteCkanResource.getId(),
                        file.toString(),
                        ContentType.create(contentType),
                        finalDownloadFileName);

                tmp.setName(remoteCkanDataset.getName());
                tmp.setOthers(remoteCkanResource.getOthers());
                int maxRetries = 5;
                for(int i = 0; i < maxRetries; ++i) {
                    try {
                        remoteCkanResource = ckanClient.updateResource(tmp);
                        break;
                    } catch(Exception e) {
                        if(i + 1 < maxRetries) {
                            logger.warn("Failed to update resource, retrying " + (i + 1) + "/" + maxRetries);
                        } else {
                            logger.error("Giving up on updating a resource after " + maxRetries, e);
                        }
                    }
                }
//					remoteCkanResource.setUrl(tmp.getUrl());
//					remoteCkanResource.setUrlType(tmp.getUrlType());

                //remoteCkanResource.set
                //remoteCkanResource = ckanClient.getResource(tmp.getId());
                // Run the metadata update again

                // This works, but retrieves the whole dataset on each resource, which we want to avoid
//					if(false) {
//						remoteCkanDataset = ckanClient.getDataset(remoteCkanDataset.getId());
//						remoteCkanResource = createOrUpdateResource(ckanClient, remoteCkanDataset, dataset, dcatDistribution);
//					}

                //DcatCkanRdfUtils.convertToCkan(remoteCkanResource, dcatDistribution);


                // FIXME upload currently destroys custom tags, hence we update the metadata again
                //remoteCkanResource = ckanClient.updateResource(remoteCkanResource);


//	                } else {
//	                    logger.info("File upload disabled. Skipping " + path);
//	                }
            }

            // Resource newDownloadUrl = ResourceFactory.createResource(remoteCkanResource.getUrl());

            //org.aksw.jena_sparql_api.rdf.collections.ResourceUtils.setProperty(dcatDistribution, DCAT.downloadURL, newDownloadUrl);

//            if (root != null) {
//                logger.info("Removing directory recursively: " + root);
//                // MoreFiles.deleteRecursively(root);
//            }
//        }
    }

    /**
     * Create or update the appropriate resource among the ones in a given dataset.
     *
     * @param ckanClient The CKAN client instance.
     * @param ckanDataset The CKAN dataset object.
     * @param resourceId The identifier of the resource to create or update.
     * @return The CkanResource instance for the created or updated resource.
     */
    public static CkanResource createOrUpdateResource(CkanClient ckanClient, CkanDataset ckanDataset, String resourceId) {
        Multimap<String, CkanResource> nameToCkanResources = Multimaps.index(
                Optional.ofNullable(ckanDataset.getResources()).orElse(Collections.emptyList()),
                CkanResource::getName);

        // Resources are required to have an ID
//        String resName = res.getTitle();
//
//        if(resName == null) {
//            if(res.isURIResource()) {
//                resName = SplitIRI.localname(res.getURI());
//            }
//        }

        String resName = resourceId;

        if(resName == null) {
            new RuntimeException("DCAT Distribution / CKAN Resource must have a name i.e. public id");
        }

        boolean isResourceCreationRequired = false;

        CkanResource remote = null;
        Collection<CkanResource> remotes = nameToCkanResources.get(resName);

        // If there are multiple resources with the same name,
        // update the first one and delete all others

        Iterator<CkanResource> it = remotes.iterator();
        remote = it.hasNext() ? it.next() : null;

        while(it.hasNext()) {
            CkanResource tmp = it.next();
            ckanClient.deleteResource(tmp.getId());
        }


        // TODO We need a file for the resource

        if(remote == null) {
            isResourceCreationRequired = true;

            remote = new CkanResource(null, ckanDataset.getName());
            remote.setId(resourceId);
            remote.setName(resName);
        }

        // Update existing attributes with non-null values
        // DcatCkanRdfUtils.convertToCkan(remote, res);

        if (isResourceCreationRequired) {
            remote = ckanClient.createResource(remote);
        } else {
            remote = ckanClient.updateResource(remote);
        }

        System.err.println("resource name: " + remote.getName());

        return remote;
    }



    /**
     * Upload a file to an *existing* record
     *
     * @param ckanClient The CKAN client instance.
     * @param datasetName The name of the CKAN dataset to which to upload the file.
     * @param resourceId The name of the resource to which to upload the dataset (w.r.t. to the dataset).
     * @param srcFilename The filename what to upload.
     * @param contentType The content type for the upload.
     * @param downloadFilename The name under which the uploaded file will be available for download.
     * @return The CkanResource for the create or updated upload.
     */
    public CkanResource uploadFile(
            CkanClient ckanClient,
            String datasetName,
            String resourceId,
            //String resourceName,
            //boolean isResourceCreationRequired,
            String srcFilename,
            ContentType contentType,
            String downloadFilename)
    {
        Log logger = getLog();

        Path path = Paths.get(srcFilename);
        if (logger.isInfoEnabled()) {
            logger.info("Updating ckan resource " + resourceId + " with content from " + path.toAbsolutePath());
        }

        contentType = contentType == null ? ContentType.DEFAULT_TEXT : contentType;
        downloadFilename = downloadFilename == null ? path.getFileName().toString() : downloadFilename;

        String apiKey = ckanClient.getCkanToken();
        String HOST = ckanClient.getCatalogUrl();// "http://ckan.host.com";

        try (CloseableHttpClient httpclient = HttpClientBuilder.create().build()) {

            // Ideally I'd like to use nio.Path instead of File but apparently the http
            // client library does not support it(?)
            File file = path.toFile();

            // SimpleDateFormat dateFormatGmt = new SimpleDateFormat("yyyyMMdd_HHmmss");
            // String date=dateFormatGmt.format(new Date());

            HttpPost postRequest;
            HttpEntity reqEntity = MultipartEntityBuilder.create()
                    .addPart("id", new StringBody(resourceId, ContentType.TEXT_PLAIN))
                    .addPart("name", new StringBody(resourceId, ContentType.TEXT_PLAIN))
                    //.addPart("name", new StringBody(resourceName, ContentType.TEXT_PLAIN))
                    .addPart("package_id", new StringBody(datasetName, ContentType.TEXT_PLAIN))
                    .addPart("upload", new FileBody(file, contentType, downloadFilename)) // , ContentType.APPLICATION_OCTET_STREAM))
                    // .addPart("file", cbFile)
                    // .addPart("url",new StringBody("path/to/save/dir", ContentType.TEXT_PLAIN))
                    // .addPart("comment",new StringBody("comments",ContentType.TEXT_PLAIN))
                    // .addPart("notes", new StringBody("notes",ContentType.TEXT_PLAIN))
                    // .addPart("author",new StringBody("AuthorName",ContentType.TEXT_PLAIN))
                    // .addPart("author_email",new StringBody("AuthorEmail",ContentType.TEXT_PLAIN))
                    // .addPart("title",new StringBody("title",ContentType.TEXT_PLAIN))
                    // .addPart("description",new StringBody("file
                    // Desc"+date,ContentType.TEXT_PLAIN))
                    .build();

            String url = false//isResourceCreationRequired
                    ? HOST + "/api/action/resource_create"
                    : HOST + "/api/action/resource_update";

            postRequest = new HttpPost(url);

            postRequest.setEntity(reqEntity);
            postRequest.setHeader("Authorization", apiKey);
            // postRequest.setHeader("X-CKAN-API-Key", myApiKey);

            HttpResponse response = httpclient.execute(postRequest);
            int statusCode = response.getStatusLine().getStatusCode();
            String status =  new BufferedReader(new InputStreamReader(response.getEntity().getContent()))
                    .lines().collect(Collectors.joining("\n"));

            if (logger.isInfoEnabled()) {
                logger.info("Upload status: " + statusCode + "\n" + status);
            }

            // TODO We could get rid of this extra request by processing the reply of the upload
            CkanResource result = ckanClient.getResource(resourceId);

            return result;
        } catch (IOException e) {
            throw new CkanException(e.getMessage(), ckanClient, e);
        }
    }

    /** Convert a URI to a PATH - if possible. */
    public static Optional<Path> pathsGet(URI uri) {
        Optional<Path> result;
        try {
            result = Optional.of(Paths.get(uri));
        } catch (Exception e) {
            result = Optional.empty();
            //throw new RuntimeException(e);
        }
        return result;
    }



//  private void uploadFileToCkan(File file) throws IOException, InterruptedException, MojoExecutionException {
//  String boundaryPrefix = "-----------------------------" + new Random().nextLong();
//  String mimeType = Files.probeContentType(file.toPath());
//  HttpClient client = HttpClient.newHttpClient();
//  HttpRequest request = HttpRequest.newBuilder()
//          .uri(URI.create(ckanUrl + "/api/action/resource_update"))
//          .header("Authorization", apiKey)
//          .header("Content-Type", "multipart/form-data; boundary=" + boundaryPrefix)
//          .POST(HttpRequest.BodyPublishers.ofString(buildMultipartData(file, boundaryPrefix, mimeType)))
//          .build();
//
//  HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
//
//  if (response.statusCode() != 200) {
//      throw new MojoExecutionException("Failed to upload file to CKAN: " + response.body());
//  }
//
//  getLog().info("File uploaded successfully to CKAN.");
//}

private void addProperty(StringBuilder builder, String boundaryPrefix, String key, String mimeType, String value) {
  builder
      .append(boundaryPrefix).append("\r\n")
      .append("Content-Disposition: form-data; name=\"").append(key).append("\"").append("\r\n")
      // .append("Content-Type: ").append(mimeType).append("\r\n")
      .append("\r\n")
      .append(value)
      // .append("\r\n")
      .append("\r\n");
}

//private void addFile(StringBuilder builder, File file) {
//  builder
//  	.append("--boundary").append(boundaryPrefix).append("\r\n")
//  builder.append("Content-Disposition: form-data")
//          .append("; name=\"upload\"")
//          .append("; filename=\"").append(file.getName()).append('"')
//          .append("; id=\"").append(id).append("\"")
//          .append("\r\n");
//  builder.append("Content-Type: ").append(mimeType).append("\r\n\r\n");
//
//  System.err.println("String: " + builder.toString());
//
//  builder.append();
//  builder.append("\r\n--").append(boundary).append("--\r\n");
//  return builder.toString();
//
//}

private String buildMultipartData(File file, String boundaryPrefix, String mimeType) throws IOException {
  StringBuilder builder = new StringBuilder();
  addProperty(builder, boundaryPrefix, "id", "text/plain", datasetId);

  builder.append(boundaryPrefix).append("\r\n");
  builder.append("Content-Disposition: form-data")
          .append("; name=\"upload\"")
          .append("; filename=\"").append(file.getName()).append('"')
          .append("\r\n");
  builder.append("Content-Type: ").append(mimeType).append("\r\n\r\n");


  builder.append(new String(Files.readAllBytes(file.toPath()), "UTF-8"));
  builder.append("\r\n").append(boundaryPrefix).append("--")
  .append("\r\n");

  String result = builder.toString();
  System.err.println("String:\n" + result);
  return result;
}


}
