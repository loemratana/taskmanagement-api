package com.ratana.task_management_api.service.ServiceImpl;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.ratana.task_management_api.config.CloudinaryConfiguration;
import com.ratana.task_management_api.service.CloudinaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;


@Service
@Slf4j
@RequiredArgsConstructor
public class CloudinaryServiceImpL implements CloudinaryService {

    private final Cloudinary cloudinary;

    @Override
    public String uploadImage(MultipartFile file) {


        try
        {
            File uploadedFile = convertMultiPartToFile(file);
            Map uploadResult = cloudinary.uploader().upload(uploadedFile, ObjectUtils.emptyMap());

            boolean delete = uploadedFile.delete();
            uploadedFile.delete();

            return uploadResult.get("secure_url").toString();
        }
        catch (Exception e)
        {
            throw new RuntimeException("Image upload failed", e);
        }

    }

    private File convertMultiPartToFile(MultipartFile multipartFile) throws IOException {
        File convertFile = new File(multipartFile.getOriginalFilename());
        FileOutputStream fos = new FileOutputStream(convertFile);
        fos.write(multipartFile.getBytes());
        fos.close();
        return convertFile;
    }
}
