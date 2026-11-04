import { useMutation, useQueryClient, type UseMutationResult } from '@tanstack/react-query';
import { createExtraction } from '@/api/extractionService';
import type { ApiError, Extraction, OcrLanguage } from '@/api/types';

interface UploadArgs {
  file: File;
  languages: OcrLanguage[];
}

export function useUploadExtraction(): UseMutationResult<
  Extraction,
  ApiError,
  UploadArgs,
  unknown
> {
  const queryClient = useQueryClient();

  return useMutation({
    mutationKey: ['extraction', 'upload'],
    mutationFn: async ({ file, languages }: UploadArgs) => {
      return await createExtraction(file, languages);
    },
    onSuccess: (data) => {
      queryClient.setQueryData(['extraction', 'status', data.id], data);
      void queryClient.invalidateQueries({ queryKey: ['documents'] });
    },
  });
}
