package org.fogbowcloud.manager.occi.core;

import static org.junit.Assert.*;

import org.fogbowcloud.manager.occi.core.Category;
import org.junit.Assert;
import org.junit.Test;

public class TestCategory {

	@Test
	public void validCategory() {
		new Category("term", "scheme", "class");
	}

	@Test(expected=IllegalArgumentException.class)
	public void invalidCategoryEmptyTerm() {
		new Category("", "scheme", "class");
	}	
	
	@Test(expected=IllegalArgumentException.class)
	public void invalidCategoryNullTerm() {
		new Category(null, "scheme", "class");
	}		
	
	@Test(expected=IllegalArgumentException.class)
	public void invalidCategoryEmptyScheme() {
		new Category("term", "", "class");
	}		

	@Test(expected=IllegalArgumentException.class)
	public void invalidCategoryNullScheme() {
		new Category("term", null, "class");
	}		
	
	@Test(expected=IllegalArgumentException.class)
	public void invalidCategoryEmptyClass() {
		new Category("term", "scheme", "");
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void invalidCategoryNullClass() {
		new Category("term", "scheme", null);
	}	
	
	@Test
	public void validCategoryEmptyClass() {
		Category category = new Category("term", "scheme", "class");
		String headerFormat = category.toHeader();
		
		Assert.assertEquals("term; scheme=\"scheme\"; class=\"class\"", headerFormat);
	}			
	
}
