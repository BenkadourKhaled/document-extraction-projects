package dz.DocuScan.document.extractor;
import dz.DocuScan.document.dto.PageResult;
import java.awt.image.BufferedImage;
public interface DocumentExtractor { PageResult extractPage(int pageNumber, BufferedImage image, String languages) throws Exception; }
