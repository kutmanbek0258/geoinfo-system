package kg.geoinfo.system.authservice.service;

import kg.geoinfo.system.authservice.dao.entity.UserEntity;
import kg.geoinfo.system.authservice.dto.FileStoreDto;
import kg.geoinfo.system.authservice.dto.RegistrationDto;

import java.util.UUID;

public interface UserService {

    /**
     * Создание пользователя на основе регистрационных данных. Пользователь будет не активирован.
     *
     * @param userDto данные указанные при регистрации
     */
    UserEntity saveUser(RegistrationDto userDto);

    /**
     * Активация пользователя
     *
     * @param userId   уникальный идентификатор пользователя
     * @param password пароль пользователя
     */
    UserEntity firstActivation(UUID userId, String password);

    /**
     * Создать пользователя и сразу активировать
     */
    UserEntity saveAndActivateUser(RegistrationDto userDto);

    /**
     * Проверить существует ли пользователь с указанным email
     */
    boolean existByEmail(String email);

    /**
     * Найти entity пользователя по email
     */
    UserEntity findByEmail(String email);

    /**
     * Сменить пароль у пользователя с указанным email
     */
    void changePassword(String email, String password);

    /**
     * Получение аватара пользователя
     */
    UserAvatar getUserAvatar(UUID userId);

    record UserAvatar(FileStoreDto storeDto, byte[] avatar) { }
}
