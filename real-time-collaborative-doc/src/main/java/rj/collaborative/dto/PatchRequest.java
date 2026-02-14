package rj.collaborative.dto;

import com.github.difflib.patch.Patch;
import lombok.Data;

import java.util.List;
// Patch 合并请求参数
@Data
public class PatchRequest {
    private Long baseVersion;
    private List<Patch<String>> patches;  // 前端传来的 Patch 列表
}