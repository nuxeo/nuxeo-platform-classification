package org.nuxeo.ecm.classification.api.adapter;

import com.google.inject.Inject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.classification.api.ClassificationService;
import org.nuxeo.ecm.core.api.*;
import org.nuxeo.ecm.core.test.DefaultRepositoryInit;
import org.nuxeo.ecm.core.test.annotations.BackendType;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.LocalDeploy;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.nuxeo.ecm.classification.FakerClassificationResolver.FAKE_ID;
import static org.nuxeo.ecm.core.api.VersioningOption.MAJOR;

@RunWith(FeaturesRunner.class)
@Features(PlatformFeature.class)
@RepositoryConfig(type = BackendType.H2, init = DefaultRepositoryInit.class, user = "Administrator", cleanup = Granularity.METHOD)
@Deploy({ "org.nuxeo.ecm.platform.classification.api",
        "org.nuxeo.ecm.platform.classification.core" })
@LocalDeploy({ "org.nuxeo.ecm.platform.classification.api:OSGI-INF/classification-resolver-contrib.xml" })
public class ClassificationTest {

    @Inject
    ClassificationService cs;

    @Inject
    CoreSession session;

    DocumentModel root;

    DocumentModel child1;
    DocumentModel child2;

    Classification classif;

    @Before
    public void beforeMethod() throws ClientException {
        root = session.createDocumentModel("/default-domain/workspaces/test",
                "classifRoot", "ClassificationRoot");
        root = session.createDocument(root);

        child1 = session.createDocumentModel("/", "file1", "File");
        child1 = session.createDocument(child1);

        child2 = session.createDocumentModel("/", "file2", "File");
        child2 = session.createDocument(child1);

        classif = root.getAdapter(Classification.class);
        assertNotNull(classif);

        assertEquals(0, classif.getResolversDocuments().size());
    }

    @Test
    public void testAdapterResolvers() throws ClientException {
        classif.addResolver("default", child1.getId());
        session.saveDocument(classif.getDocument());

        refreshAll();

        List<Map<String, String>> resolversDocuments = classif.getResolversDocuments();
        assertEquals(1, resolversDocuments.size());
        Map<String, String> resolver = resolversDocuments.get(0);
        assertEquals("default", resolver.get("resolver"));
        assertEquals(child1.getId(), resolver.get("target"));

        assertEquals(1, classif.getClassifiedDocumentIds().size());
    }

    @Test
    public void testFakerResolver() throws ClientException {
        assertEquals(FAKE_ID,
                cs.resolveClassification(session, "fake", "something"));

        classif.addResolver("fake", child1.getId());
        classif.add(child2.getId());
        session.saveDocument(classif.getDocument());

        refreshAll();

        List<String> classifiedDocumentIds = classif.getClassifiedDocumentIds();
        assertEquals(2, classifiedDocumentIds.size());
        assertTrue(classifiedDocumentIds.contains(FAKE_ID));
        assertTrue(classifiedDocumentIds.contains(child2.getId()));

        classif.remove(child1.getId());
        session.saveDocument(classif.getDocument());

        refreshAll();

        assertEquals(0, classif.getResolversDocuments().size());
        classifiedDocumentIds = classif.getClassifiedDocumentIds();
        assertEquals(1, classifiedDocumentIds.size());
        assertFalse(classifiedDocumentIds.contains(FAKE_ID));
        assertTrue(classifiedDocumentIds.contains(child2.getId()));
    }

    @Test
    public void testLastVersionResolver() throws ClientException {
        classif.addResolver("lastVersion", child1.getId());
        session.saveDocument(classif.getDocument());

        child1.checkIn(MAJOR, null);
        child1.setPropertyValue("dc:description", "title");
        child1 = session.saveDocument(child1);

        child1.checkIn(MAJOR, null);
        child1.setPropertyValue("dc:description", "title2");
        child1 = session.saveDocument(child1);

        DocumentRef lastVersion = child1.checkIn(MAJOR, null);

        refreshAll();

        assertEquals(1, classif.getClassifiedDocuments().size());
        DocumentModel doc = classif.getClassifiedDocuments().get(0);

        assertEquals(session.getDocument(lastVersion), doc);
    }

    protected void refreshAll() throws ClientException {
        session.save();

        root = session.getDocument(root.getRef());
        child1 = session.getDocument(child1.getRef());
        child2 = session.getDocument(child2.getRef());
        classif = root.getAdapter(Classification.class);
        assertNotNull(classif);
    }

}
