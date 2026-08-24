export const EXCEL_MAX_BYTES = 5 * 1024 * 1024;
export const BACKUP_MAX_BYTES = 128 * 1024 * 1024;

interface FileValidationOptions {
  label: string;
  extensions: string[];
  maxBytes: number;
  maxSizeLabel: string;
}

export function fileValidationError(
  file: Pick<File, "name" | "size">,
  options: FileValidationOptions,
) {
  const name = file.name.toLowerCase();
  const extensions = options.extensions.map((value) =>
    value.startsWith(".") ? value.toLowerCase() : `.${value.toLowerCase()}`,
  );
  if (!extensions.some((extension) => name.endsWith(extension))) {
    return `${options.label}仅支持 ${extensions.join("、")} 文件`;
  }
  if (file.size > options.maxBytes) {
    return `${options.label}不能超过 ${options.maxSizeLabel}`;
  }
  return "";
}

export function excelFileError(file: Pick<File, "name" | "size">, label: string) {
  return fileValidationError(file, {
    label,
    extensions: [".xlsx", ".xls"],
    maxBytes: EXCEL_MAX_BYTES,
    maxSizeLabel: "5 MB",
  });
}

export function backupFileError(file: Pick<File, "name" | "size">) {
  return fileValidationError(file, {
    label: "备份文件",
    extensions: [".zip"],
    maxBytes: BACKUP_MAX_BYTES,
    maxSizeLabel: "128 MB",
  });
}
