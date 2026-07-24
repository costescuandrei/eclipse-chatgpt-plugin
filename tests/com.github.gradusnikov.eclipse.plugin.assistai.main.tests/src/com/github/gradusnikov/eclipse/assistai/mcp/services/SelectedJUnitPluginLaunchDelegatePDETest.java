package com.github.gradusnikov.eclipse.assistai.mcp.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SelectedJUnitPluginLaunchDelegatePDETest
{
    private static final String TEST_PROJECT = "SelectedJUnitPluginLaunchDelegate_TestProject";

    private IProject project;

    @BeforeEach
    public void createJavaProject() throws Exception
    {
        NullProgressMonitor monitor = new NullProgressMonitor();
        project = ResourcesPlugin.getWorkspace().getRoot().getProject( TEST_PROJECT );
        if ( project.exists() )
        {
            project.delete( true, true, monitor );
        }
        project.create( monitor );
        project.open( monitor );

        IProjectDescription description = project.getDescription();
        description.setNatureIds( new String[] { JavaCore.NATURE_ID } );
        project.setDescription( description, monitor );

        IFolder sourceFolder = project.getFolder( "src" );
        sourceFolder.create( true, true, monitor );
        IFolder outputFolder = project.getFolder( "bin" );
        outputFolder.create( true, true, monitor );

        IJavaProject javaProject = JavaCore.create( project );
        javaProject.setOutputLocation( outputFolder.getFullPath(), monitor );
        IClasspathEntry sourceEntry = JavaCore.newSourceEntry( sourceFolder.getFullPath() );
        javaProject.setRawClasspath( new IClasspathEntry[] { sourceEntry }, monitor );

        IPackageFragmentRoot sourceRoot = javaProject.getPackageFragmentRoot( sourceFolder );
        IPackageFragment packageFragment = sourceRoot.createPackageFragment( "example.selected", true, monitor );
        packageFragment.createCompilationUnit( "FirstPDETest.java",
            "package example.selected; public class FirstPDETest {}", true, monitor );
        packageFragment.createCompilationUnit( "SecondPDETest.java",
            "package example.selected; public class SecondPDETest {}", true, monitor );
        packageFragment.createCompilationUnit( "MultiMethodPDETest.java",
            "package example.selected; public class MultiMethodPDETest {"
            + " public void testAlpha() {} public void testBeta() {} }", true, monitor );
    }

    @AfterEach
    public void deleteJavaProject() throws Exception
    {
        if ( project != null && project.exists() )
        {
            project.delete( true, true, new NullProgressMonitor() );
        }
    }

    @Test
    public void testEvaluateTests_resolvesEverySelectedClass() throws Exception
    {
        ILaunchConfigurationType type = DebugPlugin.getDefault().getLaunchManager()
            .getLaunchConfigurationType( SelectedJUnitPluginLaunchDelegate.LAUNCH_CONFIGURATION_TYPE );
        assertNotNull( type );

        ILaunchConfigurationWorkingCopy configuration = type.newInstance(
            null, "SelectedJUnitPluginLaunchDelegatePDETest_multiClass" );
        configuration.setAttribute( IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME, TEST_PROJECT );
        configuration.setAttribute( SelectedJUnitPluginLaunchDelegate.ATTR_TEST_CLASSES,
            List.of( "example.selected.FirstPDETest", "example.selected.SecondPDETest" ) );

        IMember[] selected = new SelectedJUnitPluginLaunchDelegate().evaluateTests(
            configuration, new NullProgressMonitor() );

        assertEquals( 2, selected.length );
        assertInstanceOf( IType.class, selected[0] );
        assertInstanceOf( IType.class, selected[1] );
        assertEquals( "example.selected.FirstPDETest", ( (IType) selected[0] ).getFullyQualifiedName() );
        assertEquals( "example.selected.SecondPDETest", ( (IType) selected[1] ).getFullyQualifiedName() );
    }

    @Test
    public void testEvaluateTests_singleMethod_returnsIMethod() throws Exception
    {
        // When ATTR_TEST_METHOD is set with a single class, evaluateTests must return
        // exactly one IMethod so JDT's runner scopes execution to that method only.
        ILaunchConfigurationType type = DebugPlugin.getDefault().getLaunchManager()
            .getLaunchConfigurationType( SelectedJUnitPluginLaunchDelegate.LAUNCH_CONFIGURATION_TYPE );
        assertNotNull( type );

        ILaunchConfigurationWorkingCopy configuration = type.newInstance(
            null, "SelectedJUnitPluginLaunchDelegatePDETest_singleMethod" );
        configuration.setAttribute( IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME, TEST_PROJECT );
        configuration.setAttribute( SelectedJUnitPluginLaunchDelegate.ATTR_TEST_CLASSES,
            List.of( "example.selected.MultiMethodPDETest" ) );
        configuration.setAttribute( SelectedJUnitPluginLaunchDelegate.ATTR_TEST_METHOD, "testAlpha" );

        IMember[] selected = new SelectedJUnitPluginLaunchDelegate().evaluateTests(
            configuration, new NullProgressMonitor() );

        assertEquals( 1, selected.length,
            "Expected exactly one member for single-method selection" );
        assertInstanceOf( IMethod.class, selected[0],
            "Expected an IMethod, not an IType, so the runner isolates the single method" );
        assertEquals( "testAlpha", selected[0].getElementName() );
    }

    @Test
    public void testEvaluateTests_singleClass_noMethod_returnsIType() throws Exception
    {
        // A single class without ATTR_TEST_METHOD falls through to the multi-class path
        // and must return an IType (whole-class execution).
        ILaunchConfigurationType type = DebugPlugin.getDefault().getLaunchManager()
            .getLaunchConfigurationType( SelectedJUnitPluginLaunchDelegate.LAUNCH_CONFIGURATION_TYPE );
        assertNotNull( type );

        ILaunchConfigurationWorkingCopy configuration = type.newInstance(
            null, "SelectedJUnitPluginLaunchDelegatePDETest_singleClass" );
        configuration.setAttribute( IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME, TEST_PROJECT );
        configuration.setAttribute( SelectedJUnitPluginLaunchDelegate.ATTR_TEST_CLASSES,
            List.of( "example.selected.MultiMethodPDETest" ) );
        // No ATTR_TEST_METHOD → whole-class run

        IMember[] selected = new SelectedJUnitPluginLaunchDelegate().evaluateTests(
            configuration, new NullProgressMonitor() );

        assertEquals( 1, selected.length );
        assertInstanceOf( IType.class, selected[0],
            "Expected an IType for whole-class execution" );
        assertEquals( "example.selected.MultiMethodPDETest",
            ( (IType) selected[0] ).getFullyQualifiedName() );
    }
}
