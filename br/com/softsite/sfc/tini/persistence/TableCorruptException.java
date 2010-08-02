/*
* Project: DBFProject
* Date   : Mar 28, 2004
*
* Copyright (c) 2004 SoftSite Tecnologia
* Todos os direitos reservados
*/
package br.com.softsite.sfc.tini.persistence;

/**
* Exceção representa um erro na estrutura do DBF
*
* @author  regismelo
* @version 1.0
*/
public class TableCorruptException extends Exception
{

	public TableCorruptException() {
		super();
	}

	public TableCorruptException(String arg0) {
		super(arg0);
	}

}
