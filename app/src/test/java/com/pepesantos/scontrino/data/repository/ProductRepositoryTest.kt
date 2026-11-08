package com.pepesantos.scontrino.data.repository

import com.pepesantos.scontrino.data.dao.AliasDao
import com.pepesantos.scontrino.data.dao.ProductDao
import com.pepesantos.scontrino.data.model.Product
import com.pepesantos.scontrino.data.model.ProductAlias
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ProductRepositoryTest {

    private val productDao: ProductDao = mock()
    private val aliasDao: AliasDao = mock()
    private val repository = ProductRepository(productDao, aliasDao)

    private val valsoia = Product(id = 1, name = "VALSOIA 1LT", categoryId = 5, alias = "latte di soia")
    private val vemondo = Product(id = 2, name = "VEMONDO BIBITA SOIA", categoryId = 8)
    private val biscotti = Product(id = 3, name = "Biscotti", categoryId = 6)
    private val unknown = Product(id = 4, name = "XYZ 1PZ", categoryId = 1)

    private suspend fun givenProducts(vararg products: Product) {
        whenever(productDao.getAll()).thenReturn(products.toList())
    }

    @Test
    fun `joining an existing alias takes its category`() = runTest {
        givenProducts(valsoia, vemondo)
        whenever(aliasDao.get("latte di soia")).thenReturn(ProductAlias("latte di soia", 5))

        repository.setAlias(2, "Latte di soia")

        verify(aliasDao).upsert(ProductAlias("latte di soia", 5))
        verify(productDao).setAlias(2, "latte di soia")
        verify(productDao).setCategoryForAlias("latte di soia", 5)
    }

    @Test
    fun `a new alias takes the product's category`() = runTest {
        givenProducts(vemondo)
        whenever(aliasDao.get(any())).thenReturn(null)

        repository.setAlias(2, "bevanda di soia")

        verify(aliasDao).upsert(ProductAlias("bevanda di soia", 8))
    }

    @Test
    fun `picking a product as alias uses its category and groups it`() = runTest {
        givenProducts(biscotti, unknown)
        whenever(aliasDao.get(any())).thenReturn(null)

        repository.setAlias(4, "biscotti")

        verify(aliasDao).upsert(ProductAlias("Biscotti", 6))
        verify(productDao).setAlias(4, "Biscotti")
        verify(productDao).setAlias(3, "Biscotti")
        verify(productDao).setCategoryForAlias("Biscotti", 6)
    }

    @Test
    fun `an alias still in other takes the category of the product joining it`() = runTest {
        givenProducts(vemondo)
        whenever(aliasDao.get("latte di soia")).thenReturn(ProductAlias("latte di soia", 1))

        repository.setAlias(2, "latte di soia")

        verify(aliasDao).upsert(ProductAlias("latte di soia", 8))
    }

    @Test
    fun `removing an alias drops aliases left without products`() = runTest {
        givenProducts(valsoia)

        repository.setAlias(1, " ")

        verify(productDao).setAlias(1, null)
        verify(aliasDao).deleteUnused()
        verify(aliasDao, never()).upsert(any())
    }

    @Test
    fun `changing the category of a product with alias changes the whole group`() = runTest {
        whenever(productDao.search(any())).thenReturn(listOf(valsoia))

        repository.getOrCreate("VALSOIA 1LT", categoryId = 4)

        verify(aliasDao).setCategory("latte di soia", 4)
        verify(productDao).setCategoryForAlias("latte di soia", 4)
        verify(productDao, never()).setCategory(any(), any())
    }

    @Test
    fun `null alias and other category leave the product untouched`() = runTest {
        whenever(productDao.search(any())).thenReturn(listOf(valsoia))

        repository.getOrCreate("valsoia 1lt", categoryId = 1, alias = null)

        verify(productDao, never()).setAlias(any(), anyOrNull())
        verify(productDao, never()).setCategory(any(), any())
        verify(aliasDao, never()).setCategory(any(), any())
    }
}
