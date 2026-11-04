package dz.DocuScan.document.dto;
import dz.DocuScan.document.model.ExtractionMethod;
public record PageResult(int number, String text, ExtractionMethod method, Double confidence) { }
